package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.TriStorageMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import java.util.Comparator;
import java.util.zip.CRC32;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * World-owned storage persistence. Block entities retain only an id after the
 * first durable snapshot; later mutations append small idempotent journal
 * frames instead of serializing the complete catalog with the chunk.
 */
public final class StorageRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("TriStorage/Repository");
    private static final int FORMAT = 1;
    private static final int JOURNAL_MAGIC = 0x54534A31; // TSJ1
    private static final int PUBLISH_BUDGET = 256;
    private static final long COMPACTION_THRESHOLD = 16L << 20;
    private static final long MIN_IDLE_NANOS = TimeUnit.MINUTES.toNanos(5);

    private final MinecraftServer server;
    private final Path storagesPath;
    private final Path tempPath;
    private final Path manifestPath;
    private final ExecutorService ioWorker;
    private final Map<StorageId, Record> records = new HashMap<>();
    private final Map<StorageId, Ownership> ownership = new HashMap<>();
    private final ArrayDeque<Publication> publications = new ArrayDeque<>();
    private final ArrayDeque<MigrationCapture> migrations = new ArrayDeque<>();
    private final AtomicLong pendingIo = new AtomicLong();
    private volatile boolean closing;
    private final long memoryBudgetBytes;

    StorageRepository(MinecraftServer server) {
        this.server = server;
        Path root = server.getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve(TriStorageMod.MOD_ID);
        storagesPath = root.resolve("storages");
        tempPath = root.resolve("temp");
        manifestPath = root.resolve("manifest.nbt");
        try {
            Files.createDirectories(storagesPath);
            Files.createDirectories(tempPath);
            readManifest();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create TriStorage repository", exception);
        }
        ioWorker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "TriStorage-Storage-IO");
            thread.setDaemon(true);
            return thread;
        });
        long tenPercent = Runtime.getRuntime().maxMemory() / 10L;
        memoryBudgetBytes = Math.max(64L << 20, Math.min(512L << 20, tenPercent));
    }

    /**
     * Adopts legacy in-memory state or returns the already cached runtime. A
     * disk-backed runtime is loaded off-thread and published in bounded slices.
     */
    public Attachment attach(StorageRuntime candidate, String presentedToken,
                             ServerLevel world, BlockPos pos, StorageTier tier,
                             Runnable durableCallback) {
        Claim claim = claim(candidate.id(), presentedToken, world, pos, tier);
        if (!claim.valid) {
            return new Attachment(candidate, presentedToken, false);
        }
        Record existing = records.get(candidate.id());
        if (existing != null) {
            if (existing.durable) {
                durableCallback.run();
            } else {
                existing.durableCallbacks.add(durableCallback);
            }
            existing.lastAccessNanos = System.nanoTime();
            if (!existing.runtime.isReady() && !existing.loading) {
                existing.loading = true;
                loadAsync(existing);
            }
            existing.anchorCount++;
            return new Attachment(existing.runtime, claim.token, true);
        }

        Path snapshot = snapshotPath(candidate.id());
        StorageRuntime runtime = candidate;
        Record record = new Record(runtime);
        record.durableCallbacks.add(durableCallback);
        records.put(runtime.id(), record);
        record.anchorCount = 1;
        installPersistenceListener(record);
        if (Files.exists(snapshot)) {
            runtime = new StorageRuntime(candidate.id());
            runtime.beginLoading();
            record.runtime.removeListener(record.persistenceListener);
            record.runtime = runtime;
            installPersistenceListener(record);
            loadAsync(record);
        } else {
            // Legacy/new state becomes authoritative only after an atomic
            // snapshot commit. Until then the BlockEntity keeps legacy Entries.
            runtime.beginLoading();
            record.loading = true;
            migrations.add(new MigrationCapture(record));
        }
        return new Attachment(record.runtime, claim.token, true);
    }

    public PortablePreparation preparePortable(StorageId id, String presentedToken,
                                                ServerLevel world, BlockPos pos) {
        Ownership current = ownership.get(id);
        if (current == null || !current.token.equals(presentedToken)
                || !current.matches(world, pos)) {
            StorageMetrics.increment("repository.portable_invalid_ownership");
            return PortablePreparation.invalid();
        }
        Record record = records.get(id);
        if (record == null) {
            StorageMetrics.increment("repository.portable_missing_runtime");
            return PortablePreparation.invalid();
        }
        if (record.pendingWrites.get() != 0
                || record.preparedRevision != record.runtime.revision()) {
            requestPortableBarrier(record);
            StorageMetrics.increment("repository.portable_barrier_waits");
            return PortablePreparation.pending();
        }
        String rotated = UUID.randomUUID().toString();
        ownership.put(id, new Ownership(rotated, "PORTABLE", "", 0, current.tier));
        try {
            writeManifestDurable();
        } catch (RuntimeException exception) {
            ownership.put(id, current);
            LOGGER.error("Could not persist portable transition for TriStorage {}", id,
                    exception);
            StorageMetrics.increment("repository.portable_manifest_failures");
            requestPortableBarrier(record);
            return PortablePreparation.pending();
        }
        record.preparedRevision = Long.MIN_VALUE;
        record.lastAccessNanos = System.nanoTime();
        StorageMetrics.increment("repository.portable_prepared");
        return PortablePreparation.ready(rotated);
    }

    private void requestPortableBarrier(Record record) {
        if (record == null || record.portableBarrierPending || closing) {
            return;
        }
        long targetRevision = record.runtime.revision();
        record.portableBarrierPending = true;
        submitIo(() -> {
            boolean success = false;
            try {
                Path journal = journalPath(record.runtime.id());
                if (Files.exists(journal)) {
                    try (FileChannel channel = FileChannel.open(
                            journal, StandardOpenOption.WRITE)) {
                        channel.force(false);
                    }
                }
                success = true;
            } finally {
                boolean completed = success;
                server.execute(() -> {
                    record.portableBarrierPending = false;
                    if (completed && record.pendingWrites.get() == 0
                            && record.runtime.revision() == targetRevision) {
                        record.preparedRevision = targetRevision;
                        StorageMetrics.increment("repository.portable_barriers_completed");
                    } else if (!completed) {
                        // A failed force must remain retryable. Leaving this flag
                        // set used to wedge the Core until the world restarted.
                        record.preparedRevision = Long.MIN_VALUE;
                        StorageMetrics.increment("repository.portable_barriers_failed");
                    }
                });
            }
        });
    }

    /** Reverts a prepared break when another block-break listener cancels it. */
    public String cancelPortable(StorageId id, String presentedToken,
                                 ServerLevel world, BlockPos pos) {
        Ownership current = ownership.get(id);
        if (current == null || !"PORTABLE".equals(current.lifecycle)
                || !current.token.equals(presentedToken)) {
            return null;
        }
        String rotated = UUID.randomUUID().toString();
        ownership.put(id, new Ownership(rotated, "ACTIVE",
                world.dimension().location().toString(), pos.asLong(), current.tier));
        writeManifestDurable();
        return rotated;
    }

    private boolean awaitDurableBarrier(StorageId id) {
        if (closing) {
            return false;
        }
        CompletableFuture<Void> barrier = new CompletableFuture<>();
        ioWorker.execute(() -> {
            try {
                Path journal = journalPath(id);
                if (Files.exists(journal)) {
                    try (FileChannel channel = FileChannel.open(
                            journal, StandardOpenOption.WRITE)) {
                        channel.force(false);
                    }
                }
                barrier.complete(null);
            } catch (IOException exception) {
                barrier.completeExceptionally(exception);
            }
        });
        try {
            barrier.get(10, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.util.concurrent.ExecutionException | TimeoutException exception) {
            return false;
        }
    }

    public void releaseAnchor(StorageId id) {
        Record record = records.get(id);
        if (record != null && record.anchorCount > 0) {
            record.anchorCount--;
            record.lastAccessNanos = System.nanoTime();
        }
    }

    public void touch(StorageId id) {
        Record record = records.get(id);
        if (record != null) {
            record.lastAccessNanos = System.nanoTime();
        }
    }

    public Diagnostics diagnostics() {
        int loaded = 0;
        int loading = 0;
        long types = 0;
        long items = 0;
        long bytes = 0;
        for (Record record : records.values()) {
            if (record.runtime.isReady()) {
                loaded++;
                types += record.runtime.storedTypes();
                items += record.runtime.totalItems();
            } else {
                loading++;
            }
            bytes += record.runtime.estimatedBytes();
        }
        return new Diagnostics(records.size(), loaded, loading, types, items,
                bytes, memoryBudgetBytes, pendingIo.get(),
                publications.size() + migrations.size());
    }

    public List<StorageSummary> listStorages() {
        return ownership.keySet().stream()
                .sorted(Comparator.comparing(StorageId::toString))
                .map(this::summary)
                .toList();
    }

    public StorageSummary inspect(StorageId id) {
        if (!ownership.containsKey(id)) {
            return new StorageSummary(id, "MISSING", false, 0, 0, 0, 0);
        }
        Record record = records.get(id);
        if (record == null && Files.exists(snapshotPath(id))) {
            StorageRuntime runtime = new StorageRuntime(id);
            runtime.beginLoading();
            record = new Record(runtime);
            record.durable = true;
            record.loading = true;
            records.put(id, record);
            installPersistenceListener(record);
            loadAsync(record);
        }
        return summary(id);
    }

    private StorageSummary summary(StorageId id) {
        Ownership owner = ownership.get(id);
        Record record = records.get(id);
        boolean ready = record != null && record.runtime.isReady();
        return new StorageSummary(id, owner == null ? "MISSING" : owner.lifecycle,
                ready, record == null ? 0 : record.anchorCount,
                ready ? record.runtime.installedChests() : 0,
                ready ? record.runtime.storedTypes() : 0,
                ready ? record.runtime.totalItems() : 0);
    }

    /** Explicit operator recovery. The repeated confirmation is enforced by the command. */
    public PortableRecovery recoverPortable(StorageId id) {
        StorageSummary status = inspect(id);
        Record record = records.get(id);
        if (!status.ready || record == null || record.anchorCount != 0
                || record.pendingWrites.get() != 0 || !awaitDurableBarrier(id)) {
            return new PortableRecovery(status, "NOT_READY", "", "");
        }
        String tier = ownership.get(id).tier;
        String token = UUID.randomUUID().toString();
        ownership.put(id, new Ownership(token, "PORTABLE", "", 0, tier));
        writeManifestDurable();
        return new PortableRecovery(summary(id), "RECOVERED", token, tier);
    }

    /** Deletes only a fully loaded, unanchored storage with no chests or items. */
    public String purgeEmpty(StorageId id) {
        StorageSummary status = inspect(id);
        Record record = records.get(id);
        if (!status.ready || record == null) {
            return "NOT_READY";
        }
        if (record.anchorCount != 0 || record.pendingWrites.get() != 0) {
            return "IN_USE";
        }
        if (record.runtime.totalItems() != 0 || record.runtime.installedChests() != 0) {
            return "NOT_EMPTY";
        }
        record.runtime.removeListener(record.persistenceListener);
        records.remove(id);
        ownership.remove(id);
        writeManifestDurable();
        try {
            Files.deleteIfExists(snapshotPath(id));
            Files.deleteIfExists(journalPath(id));
            Files.deleteIfExists(sealedJournalPath(id));
        } catch (IOException exception) {
            LOGGER.error("Could not remove empty TriStorage {}", id, exception);
            return "IO_ERROR";
        }
        StorageMetrics.increment("repository.admin_purges");
        return "PURGED";
    }

    public void tick() {
        int remaining = PUBLISH_BUDGET;
        while (remaining > 0 && !migrations.isEmpty()) {
            MigrationCapture migration = migrations.peek();
            int consumed = migration.capture(remaining);
            remaining -= consumed;
            if (migration.complete()) {
                migrations.remove();
                persistMigration(migration);
            }
            if (consumed == 0) {
                break;
            }
        }
        while (remaining > 0 && !publications.isEmpty()) {
            Publication publication = publications.peek();
            int consumed = publication.publish(remaining);
            remaining -= consumed;
            if (publication.complete()) {
                publications.remove();
                publication.runtime.finishLoading(publication.prepared.revision);
                Record record = records.get(publication.runtime.id());
                if (record != null) {
                    record.loading = false;
                    markDurable(record);
                }
                StorageMetrics.increment("repository.loads_completed");
            }
            if (consumed == 0) {
                break;
            }
        }
        if (server.getTickCount() % 200 == 0) {
            evictIdleRuntimes();
        }
    }

    private void persistMigration(MigrationCapture migration) {
        Record record = migration.record;
        submitIo(() -> {
            writeSnapshot(record.runtime.id(), migration.entries,
                    record.runtime.installedChests(), migration.revision);
            server.execute(() -> {
                record.runtime.finishLoading(migration.revision);
                record.loading = false;
                markDurable(record);
                StorageMetrics.increment("repository.migrations_completed");
            });
        });
    }

    public void closeAndFlush() {
        closing = true;
        for (Record record : List.copyOf(records.values())) {
            if (record.runtime.isReady()) {
                checkpointAsync(record, false);
            }
        }
        ioWorker.shutdown();
        try {
            if (!ioWorker.awaitTermination(30, TimeUnit.SECONDS)) {
                ioWorker.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ioWorker.shutdownNow();
        }
    }

    private void installPersistenceListener(Record record) {
        StorageRuntime.Listener listener = (runtime, changes) -> appendChange(record, changes);
        record.persistenceListener = listener;
        record.runtime.addListener(listener);
    }

    private void loadAsync(Record record) {
        record.loading = true;
        submitIo(() -> {
            Prepared prepared = readPrepared(record.runtime.id());
            server.execute(() -> publications.add(new Publication(record.runtime, prepared)));
        });
    }

    private void evictIdleRuntimes() {
        long total = records.values().stream()
                .mapToLong(record -> record.runtime.estimatedBytes()).sum();
        if (total <= memoryBudgetBytes) {
            return;
        }
        long now = System.nanoTime();
        List<Record> candidates = records.values().stream()
                .filter(record -> record.anchorCount == 0)
                .filter(record -> record.durable && record.runtime.isReady())
                .filter(record -> record.pendingWrites.get() == 0)
                .filter(record -> !record.loading && !record.evictionPending)
                .filter(record -> now - record.lastAccessNanos >= MIN_IDLE_NANOS)
                .sorted((left, right) -> Long.compare(
                        left.lastAccessNanos, right.lastAccessNanos))
                .toList();
        for (Record record : candidates) {
            if (total <= memoryBudgetBytes) {
                break;
            }
            long released = record.runtime.estimatedBytes();
            record.evictionPending = true;
            StorageRuntime oldRuntime = record.runtime;
            submitIo(() -> {
                Path journal = journalPath(oldRuntime.id());
                if (Files.exists(journal)) {
                    try (FileChannel channel = FileChannel.open(
                            journal, StandardOpenOption.WRITE)) {
                        channel.force(false);
                    }
                }
                server.execute(() -> {
                    if (record.anchorCount != 0 || record.runtime != oldRuntime
                            || record.pendingWrites.get() != 0) {
                        record.evictionPending = false;
                        return;
                    }
                    oldRuntime.removeListener(record.persistenceListener);
                    StorageRuntime placeholder = new StorageRuntime(oldRuntime.id());
                    placeholder.beginLoading();
                    record.runtime = placeholder;
                    installPersistenceListener(record);
                    record.evictionPending = false;
                    StorageMetrics.increment("repository.runtime_evictions");
                });
            });
            total -= released;
        }
    }

    private void appendChange(Record record, StorageRuntime.ChangeSet changes) {
        if (closing) {
            return;
        }
        long captureStarted = StorageMetrics.startTimer();
        record.preparedRevision = Long.MIN_VALUE;
        StorageId storageId = record.runtime.id();
        long sequence = ++record.persistenceSequence;
        long revision = changes.contentRevision();
        int installedChests = record.runtime.installedChests();
        List<JournalOperation> operations = new ArrayList<>(changes.amountDeltas().size());
        for (long entryId : changes.amountDeltas().keySet()) {
            StorageRuntime.SnapshotEntry entry = record.runtime.snapshotEntry(entryId);
            if (entry == null) {
                operations.add(new JournalOperation(entryId, ItemStack.EMPTY, 0, true));
            } else {
                operations.add(new JournalOperation(entryId, entry.stack(),
                        entry.count(), false));
            }
        }
        StorageMetrics.stopTimer("repository.journal_capture", captureStarted);
        long now = System.nanoTime();
        boolean force = now - record.lastForceNanos >= TimeUnit.SECONDS.toNanos(1);
        if (force) {
            record.lastForceNanos = now;
        }
        record.pendingWrites.incrementAndGet();
        submitIo(() -> {
            try {
                long serializeStarted = StorageMetrics.startTimer();
                CompoundTag frame = new CompoundTag();
                frame.putInt("FormatVersion", FORMAT);
                frame.putLong("Sequence", sequence);
                frame.putLong("Revision", revision);
                frame.putInt("InstalledChests", installedChests);
                ListTag serialized = new ListTag();
                for (JournalOperation captured : operations) {
                    CompoundTag operation = new CompoundTag();
                    operation.putLong("EntryId", captured.entryId());
                    if (captured.removed()) {
                        operation.putBoolean("Removed", true);
                    } else {
                        operation.put("Stack", captured.stack().save(new CompoundTag()));
                        operation.putLong("Count", captured.count());
                    }
                    serialized.add(operation);
                }
                frame.put("Operations", serialized);
                StorageMetrics.stopTimer("repository.journal_serialize", serializeStarted);
                appendFrame(journalPath(storageId), frame, force);
                compactIfNeeded(storageId);
            } finally {
                record.pendingWrites.decrementAndGet();
            }
        });
        StorageMetrics.increment("repository.journal_batches");
        StorageMetrics.add("repository.journal_operations", operations.size());
    }

    private void checkpointAsync(Record record, boolean migrationBarrier) {
        List<StorageRuntime.SnapshotEntry> entries = record.runtime.snapshotEntries();
        int chests = record.runtime.installedChests();
        long revision = record.runtime.revision();
        submitIo(() -> {
            writeSnapshot(record.runtime.id(), entries, chests, revision);
            Files.deleteIfExists(journalPath(record.runtime.id()));
            Files.deleteIfExists(sealedJournalPath(record.runtime.id()));
            if (migrationBarrier) {
                server.execute(() -> markDurable(record));
            }
        });
    }

    private void markDurable(Record record) {
        record.durable = true;
        if (record.pendingWrites.get() == 0) {
            record.preparedRevision = record.runtime.revision();
        }
        for (Runnable callback : List.copyOf(record.durableCallbacks)) {
            callback.run();
        }
        record.durableCallbacks.clear();
    }

    private Claim claim(StorageId id, String presentedToken,
                        ServerLevel world, BlockPos pos, StorageTier tier) {
        String safeToken = presentedToken == null || presentedToken.isBlank()
                ? UUID.randomUUID().toString() : presentedToken;
        Ownership current = ownership.get(id);
        String dimension = world.dimension().location().toString();
        if (current == null) {
            ownership.put(id, new Ownership(safeToken, "ACTIVE", dimension, pos.asLong(),
                    tier.name()));
            writeManifestDurable();
            return new Claim(safeToken, true);
        }
        if ("ACTIVE".equals(current.lifecycle)) {
            boolean valid = current.token.equals(safeToken) && current.matches(world, pos);
            if (valid && current.tier.isEmpty()) {
                // Manifests written before tiers were recorded learn them on load.
                ownership.put(id, new Ownership(current.token, current.lifecycle,
                        current.dimension, current.blockPos, tier.name()));
                writeManifestDurable();
            }
            return new Claim(current.token, valid);
        }
        if (!"PORTABLE".equals(current.lifecycle) || !current.token.equals(safeToken)) {
            return new Claim(current.token, false);
        }
        String rotated = UUID.randomUUID().toString();
        ownership.put(id, new Ownership(rotated, "ACTIVE", dimension, pos.asLong(),
                tier.name()));
        writeManifestDurable();
        return new Claim(rotated, true);
    }

    private void readManifest() throws IOException {
        if (!Files.exists(manifestPath)) {
            return;
        }
        CompoundTag root = NbtIo.readCompressed(manifestPath.toFile());
        ListTag list = root.getList("Storages", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag stored = list.getCompound(index);
            try {
                StorageId id = StorageId.parse(stored.getString("StorageId"));
                ownership.put(id, new Ownership(stored.getString("Token"),
                        stored.getString("Lifecycle"), stored.getString("Dimension"),
                        stored.getLong("BlockPos"), stored.getString("Tier")));
            } catch (IllegalArgumentException ignored) {
                StorageMetrics.increment("repository.invalid_manifest_entries");
            }
        }
    }

    private void writeManifestDurable() {
        CompoundTag root = new CompoundTag();
        root.putInt("FormatVersion", FORMAT);
        ListTag list = new ListTag();
        for (Map.Entry<StorageId, Ownership> entry : ownership.entrySet()) {
            CompoundTag stored = new CompoundTag();
            stored.putString("StorageId", entry.getKey().toString());
            stored.putString("Token", entry.getValue().token);
            stored.putString("Lifecycle", entry.getValue().lifecycle);
            stored.putString("Dimension", entry.getValue().dimension);
            stored.putLong("BlockPos", entry.getValue().blockPos);
            stored.putString("Tier", entry.getValue().tier);
            list.add(stored);
        }
        root.put("Storages", list);
        Path temporary = tempPath.resolve("manifest.tmp");
        try {
            NbtIo.writeCompressed(root, temporary.toFile());
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, manifestPath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, manifestPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not commit TriStorage manifest", exception);
        }
    }

    private void submitIo(IoTask task) {
        pendingIo.incrementAndGet();
        ioWorker.execute(() -> {
            try {
                task.run();
            } catch (Exception exception) {
                LOGGER.error("TriStorage storage IO failed", exception);
                StorageMetrics.increment("repository.io_errors");
            } finally {
                pendingIo.decrementAndGet();
            }
        });
    }

    private Prepared readPrepared(StorageId id) throws IOException {
        Path snapshot = snapshotPath(id);
        CompoundTag snapshotRoot = Files.exists(snapshot)
                ? NbtIo.readCompressed(snapshot.toFile()) : null;
        List<CompoundTag> frames = new ArrayList<>();
        frames.addAll(readFrames(sealedJournalPath(id)));
        frames.addAll(readFrames(journalPath(id)));
        return replay(snapshotRoot, frames);
    }

    /**
     * Rebuilds a catalog from its snapshot and journal frames. A frame stores
     * the final state of every entry it touched, so replaying a frame that the
     * snapshot already contains rolls those entries back. That happened when a
     * compaction left a sealed journal behind and a later checkpoint wrote a
     * newer snapshot. Frames at or below the snapshot revision are skipped.
     */
    static Prepared replay(@Nullable CompoundTag snapshotRoot, List<CompoundTag> frames) {
        LinkedHashMap<Long, CompoundTag> entries = new LinkedHashMap<>();
        int chests = 0;
        long revision = 0;
        long snapshotRevision = Long.MIN_VALUE;
        if (snapshotRoot != null) {
            chests = Math.max(0, snapshotRoot.getInt("InstalledChests"));
            revision = Math.max(0, snapshotRoot.getLong("Revision"));
            snapshotRevision = revision;
            ListTag list = snapshotRoot.getList("Entries", Tag.TAG_COMPOUND);
            for (int index = 0; index < list.size(); index++) {
                CompoundTag entry = list.getCompound(index);
                entries.put(entry.getLong("EntryId"), entry.copy());
            }
        }
        int staleFrames = 0;
        for (CompoundTag frame : frames) {
            long frameRevision = frame.getLong("Revision");
            if (frameRevision <= snapshotRevision) {
                staleFrames++;
                continue;
            }
            chests = Math.max(0, frame.getInt("InstalledChests"));
            revision = Math.max(revision, frameRevision);
            ListTag operations = frame.getList("Operations", Tag.TAG_COMPOUND);
            for (int index = 0; index < operations.size(); index++) {
                CompoundTag operation = operations.getCompound(index);
                long entryId = operation.getLong("EntryId");
                if (operation.getBoolean("Removed")) {
                    entries.remove(entryId);
                } else {
                    entries.put(entryId, operation.copy());
                }
            }
        }
        if (staleFrames > 0) {
            StorageMetrics.add("repository.stale_frames_skipped", staleFrames);
        }
        return new Prepared(chests, revision, new ArrayList<>(entries.values()));
    }

    private void compactIfNeeded(StorageId id) throws IOException {
        Path journal = journalPath(id);
        Path sealed = sealedJournalPath(id);
        if (Files.exists(sealed)) {
            // An earlier compaction stopped after sealing. Fold the leftover
            // into a snapshot first; otherwise compaction stays blocked and the
            // journal grows without bound.
            writePreparedSnapshot(id, readPrepared(id));
            Files.deleteIfExists(sealed);
            StorageMetrics.increment("repository.sealed_journals_recovered");
        }
        if (!Files.exists(journal) || Files.size(journal) < COMPACTION_THRESHOLD) {
            return;
        }
        try {
            Files.move(journal, sealed, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unsupportedAtomicMove) {
            Files.move(journal, sealed, StandardCopyOption.REPLACE_EXISTING);
        }
        Prepared prepared = readPrepared(id);
        writePreparedSnapshot(id, prepared);
        Files.deleteIfExists(sealed);
        StorageMetrics.increment("repository.compactions");
    }

    private void writePreparedSnapshot(StorageId id, Prepared prepared) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("FormatVersion", FORMAT);
        root.putString("StorageId", id.toString());
        root.putLong("Revision", prepared.revision);
        root.putInt("InstalledChests", prepared.chests);
        ListTag list = new ListTag();
        for (CompoundTag entry : prepared.entries) {
            list.add(entry.copy());
        }
        root.put("Entries", list);
        writeSnapshotRoot(id, root);
    }

    private void writeSnapshot(StorageId id, List<StorageRuntime.SnapshotEntry> entries,
                               int chests, long revision) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("FormatVersion", FORMAT);
        root.putString("StorageId", id.toString());
        root.putLong("Revision", revision);
        root.putInt("InstalledChests", chests);
        ListTag list = new ListTag();
        for (StorageRuntime.SnapshotEntry entry : entries) {
            CompoundTag stored = new CompoundTag();
            stored.putLong("EntryId", entry.id());
            stored.put("Stack", entry.stack().save(new CompoundTag()));
            stored.putLong("Count", entry.count());
            list.add(stored);
        }
        root.put("Entries", list);
        writeSnapshotRoot(id, root);
        StorageMetrics.increment("repository.snapshots");
    }

    private void writeSnapshotRoot(StorageId id, CompoundTag root) throws IOException {
        Path temporary = tempPath.resolve(id + ".snapshot.tmp");
        NbtIo.writeCompressed(root, temporary.toFile());
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        try {
            Files.move(temporary, snapshotPath(id), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unsupportedAtomicMove) {
            Files.move(temporary, snapshotPath(id), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void appendFrame(Path journal, CompoundTag frame, boolean force)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(frame, output);
        }
        byte[] payload = bytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(payload);
        Files.createDirectories(journal.getParent());
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(journal,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            output.writeInt(JOURNAL_MAGIC);
            output.writeInt(FORMAT);
            output.writeInt(payload.length);
            output.write(payload);
            output.writeInt((int) crc.getValue());
        }
        if (force) {
            try (FileChannel channel = FileChannel.open(journal, StandardOpenOption.WRITE)) {
                channel.force(false);
            }
        }
    }

    private static List<CompoundTag> readFrames(Path journal) throws IOException {
        if (!Files.exists(journal)) {
            return List.of();
        }
        List<CompoundTag> result = new ArrayList<>();
        long validBytes = 0;
        try (CountingInputStream counting = new CountingInputStream(
                Files.newInputStream(journal));
             DataInputStream input = new DataInputStream(counting)) {
            while (true) {
                try {
                    if (input.readInt() != JOURNAL_MAGIC || input.readInt() != FORMAT) {
                        break;
                    }
                    int length = input.readInt();
                    if (length < 0 || length > 64 * 1024 * 1024) {
                        break;
                    }
                    byte[] payload = input.readNBytes(length);
                    if (payload.length != length) {
                        break;
                    }
                    int expectedCrc = input.readInt();
                    CRC32 crc = new CRC32();
                    crc.update(payload);
                    if ((int) crc.getValue() != expectedCrc) {
                        break;
                    }
                    try (DataInputStream frameInput =
                                 new DataInputStream(new ByteArrayInputStream(payload))) {
                        result.add(NbtIo.read(frameInput));
                    }
                    validBytes = counting.count;
                } catch (EOFException incompleteTail) {
                    break;
                } catch (IOException corruptFrame) {
                    break;
                }
            }
        }
        long fileSize = Files.size(journal);
        if (validBytes < fileSize) {
            try (FileChannel channel = FileChannel.open(journal, StandardOpenOption.WRITE)) {
                channel.truncate(validBytes);
                channel.force(false);
            }
            StorageMetrics.increment("repository.journal_tails_truncated");
        }
        return result;
    }

    private Path snapshotPath(StorageId id) {
        return storagesPath.resolve(id + ".snapshot.nbt");
    }

    private Path journalPath(StorageId id) {
        return storagesPath.resolve(id + ".journal");
    }

    private Path sealedJournalPath(StorageId id) {
        return storagesPath.resolve(id + ".journal.sealed");
    }

    @FunctionalInterface
    private interface IoTask {
        void run() throws Exception;
    }

    private static final class Record {
        private StorageRuntime runtime;
        private StorageRuntime.Listener persistenceListener;
        private final List<Runnable> durableCallbacks = new ArrayList<>();
        private long persistenceSequence;
        private long lastAccessNanos = System.nanoTime();
        private long lastForceNanos;
        private boolean durable;
        private int anchorCount;
        private boolean loading;
        private boolean evictionPending;
        private boolean portableBarrierPending;
        private long preparedRevision = Long.MIN_VALUE;
        private final AtomicLong pendingWrites = new AtomicLong();

        private Record(StorageRuntime runtime) {
            this.runtime = runtime;
        }
    }

    public record Attachment(StorageRuntime runtime, String ownershipToken, boolean valid) {
    }

    public record Diagnostics(int knownStorages, int loadedRuntimes,
                              int loadingRuntimes, long storedTypes,
                              long storedItems, long estimatedBytes,
                              long memoryBudgetBytes, long pendingIoTasks,
                              int pendingPublications) {
    }

    public record StorageSummary(StorageId id, String lifecycle, boolean ready,
                                 int anchors, int installedChests,
                                 int storedTypes, long storedItems) {
    }

    /** {@code tier} is the recorded {@link StorageTier} name, or empty for old manifests. */
    public record PortableRecovery(StorageSummary summary, String result, String token,
                                   String tier) {
    }

    public enum PortablePreparationStatus {
        READY,
        PENDING,
        INVALID
    }

    public record PortablePreparation(PortablePreparationStatus status, String token) {
        public static PortablePreparation ready(String token) {
            return new PortablePreparation(PortablePreparationStatus.READY, token);
        }

        public static PortablePreparation pending() {
            return new PortablePreparation(PortablePreparationStatus.PENDING, "");
        }

        public static PortablePreparation invalid() {
            return new PortablePreparation(PortablePreparationStatus.INVALID, "");
        }
    }

    private record Claim(String token, boolean valid) {
    }

    private record Ownership(String token, String lifecycle, String dimension, long blockPos,
                             String tier) {
        private boolean matches(ServerLevel world, BlockPos pos) {
            return dimension.equals(world.dimension().location().toString())
                    && blockPos == pos.asLong();
        }
    }

    record Prepared(int chests, long revision, List<CompoundTag> entries) {
    }

    private record JournalOperation(long entryId, ItemStack stack,
                                    long count, boolean removed) {
    }

    private static final class Publication {
        private final StorageRuntime runtime;
        private final Prepared prepared;
        private int index;

        private Publication(StorageRuntime runtime, Prepared prepared) {
            this.runtime = runtime;
            this.prepared = prepared;
            runtime.loadInstalledChests(prepared.chests);
        }

        private int publish(int budget) {
            int start = index;
            int end = Math.min(prepared.entries.size(), index + budget);
            while (index < end) {
                CompoundTag stored = prepared.entries.get(index++);
                ItemStack stack = ItemStack.of(stored.getCompound("Stack"));
                runtime.loadEntry(stored.getLong("EntryId"), stack,
                        Math.max(0, stored.getLong("Count")));
            }
            return index - start;
        }

        private boolean complete() {
            return index >= prepared.entries.size();
        }
    }

    private static final class MigrationCapture {
        private final Record record;
        private final StorageRuntime.SnapshotCursor cursor;
        private final List<StorageRuntime.SnapshotEntry> entries = new ArrayList<>();
        private final long revision;

        private MigrationCapture(Record record) {
            this.record = record;
            this.cursor = record.runtime.snapshotCursor();
            this.revision = record.runtime.revision();
        }

        private int capture(int budget) {
            List<StorageRuntime.SnapshotEntry> batch = cursor.next(budget);
            entries.addAll(batch);
            return batch.size();
        }

        private boolean complete() {
            return cursor.complete();
        }
    }

    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        private CountingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                count += read;
            }
            return read;
        }
    }
}
