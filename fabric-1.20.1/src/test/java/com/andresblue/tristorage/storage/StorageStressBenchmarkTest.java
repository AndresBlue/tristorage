package com.andresblue.tristorage.storage;

import com.google.gson.GsonBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * High-volume deterministic benchmark. Timings are reported rather than used
 * as assertions; structural work counters remain stable across hardware.
 */
@Tag("stress")
class StorageStressBenchmarkTest {
    private static final int TYPE_CAPACITY = 120_000;
    private static final long ITEM_CAPACITY = 20_000_000L;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fullStressMatrix() throws Exception {
        StorageMetrics.setEnabled(true);
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            for (int size : new int[]{1_000, 10_000, 25_000, 50_000, 100_000}) {
                results.add(runScale(size));
            }
            results.add(runEightActorMutationScenario(100_000, 250));
            results.add(runRandomizedModelScenario(25_000, 100_000, 0x54524953L));
            results.add(runHeavyNbtScenario(10_000));
            results.add(runJournalScenario(2_000));
            writeReport(results);
        } finally {
            StorageTickCoordinator.flush();
            StorageMetrics.setEnabled(false);
        }
    }

    private static Map<String, Object> runScale(int types) {
        StorageMetrics.reset();
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        long buildStarted = System.nanoTime();
        for (int index = 0; index < types; index++) {
            assertEquals(64, runtime.insert(variant(index), 64,
                    TYPE_CAPACITY, ITEM_CAPACITY));
        }
        StorageTickCoordinator.flush();
        long buildNanos = System.nanoTime() - buildStarted;

        long firstPageNanos = time(() -> assertEquals(54,
                runtime.browse(0, 54, StorageRuntime.EntryOrder.REGISTRY, all())
                        .entries().size()));
        int deepPage = Math.max(0, runtime.storedTypes() / 54 - 2);
        long deepPageNanos = time(() -> assertFalse(runtime.browse(
                deepPage, 54, StorageRuntime.EntryOrder.REGISTRY, all())
                .entries().isEmpty()));
        long countPageNanos = time(() -> assertEquals(54,
                runtime.browse(0, 54, StorageRuntime.EntryOrder.COUNT, all())
                        .entries().size()));

        TerminalFilter.Selection search = TerminalFilter.sanitize(
                "paper", TerminalFilter.CategoryMode.NONE, TerminalFilter.ALL);
        long coldSearchNanos = time(() -> assertEquals(types,
                runtime.browse(0, 54, StorageRuntime.EntryOrder.REGISTRY, search)
                        .filteredTypes()));
        long warmSearchNanos = time(() -> assertEquals(types,
                runtime.browse(0, 54, StorageRuntime.EntryOrder.REGISTRY, search)
                        .filteredTypes()));
        TerminalFilter.Selection modCategory = TerminalFilter.sanitize(
                "", TerminalFilter.CategoryMode.MOD, "minecraft");
        long categoryNanos = time(() -> assertEquals(types,
                runtime.browse(0, 54, StorageRuntime.EntryOrder.REGISTRY, modCategory)
                        .filteredTypes()));

        Map<String, Long> counters = StorageMetrics.snapshot();
        assertEquals(types, runtime.storedTypes());
        assertEquals(types * 64L, runtime.totalItems());
        assertEquals(1L, counters.getOrDefault("mutation_batches", 0L));
        assertEquals(0L, counters.getOrDefault("full_sorts", 0L));
        assertEquals(2L, counters.getOrDefault("view_builds", 0L));
        return result("scale-" + types, types, buildNanos,
                Map.of("firstPageNanos", firstPageNanos,
                        "deepPageNanos", deepPageNanos,
                        "countPageNanos", countPageNanos,
                        "coldSearchNanos", coldSearchNanos,
                        "warmSearchNanos", warmSearchNanos,
                        "categoryNanos", categoryNanos,
                        "estimatedBytes", runtime.estimatedBytes()), counters);
    }

    private static Map<String, Object> runEightActorMutationScenario(
            int types, int simulatedTicks) {
        StorageMetrics.reset();
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        List<ItemStack> actorTemplates = new ArrayList<>();
        for (int index = 0; index < types; index++) {
            ItemStack stack = variant(index);
            runtime.insert(stack, 64, TYPE_CAPACITY, ITEM_CAPACITY);
            if (index < 8) {
                actorTemplates.add(stack.copy());
            }
        }
        StorageTickCoordinator.flush();
        TerminalFilter.Selection search = TerminalFilter.sanitize(
                "paper", TerminalFilter.CategoryMode.NONE, TerminalFilter.ALL);
        List<StorageRuntime.ViewLease> leases = new ArrayList<>();
        for (int actor = 0; actor < 8; actor++) {
            leases.add(runtime.retainView(StorageRuntime.EntryOrder.COUNT, search));
        }
        runtime.browse(0, 54, StorageRuntime.EntryOrder.COUNT, search);
        StorageMetrics.reset();

        long[] tickNanos = new long[simulatedTicks];
        for (int tick = 0; tick < simulatedTicks; tick++) {
            long started = System.nanoTime();
            for (int actor = 0; actor < 8; actor++) {
                runtime.insert(actorTemplates.get(actor), 1,
                        TYPE_CAPACITY, ITEM_CAPACITY);
            }
            StorageTickCoordinator.flush();
            for (int actor = 0; actor < 8; actor++) {
                StorageRuntime.BrowseResult page = runtime.browse(
                        0, 54, StorageRuntime.EntryOrder.COUNT, search);
                assertEquals(types, page.filteredTypes());
                assertEquals(54, page.entries().size());
            }
            tickNanos[tick] = System.nanoTime() - started;
        }
        leases.forEach(StorageRuntime.ViewLease::close);

        Arrays.sort(tickNanos);
        Map<String, Long> counters = StorageMetrics.snapshot();
        assertEquals(simulatedTicks,
                counters.getOrDefault("mutation_batches", 0L));
        assertEquals(0L, counters.getOrDefault("full_sorts", 0L));
        assertEquals(0L, counters.getOrDefault("full_scans", 0L));
        assertTrue(counters.getOrDefault("page_snapshot_hits", 0L)
                >= simulatedTicks * 7L);
        return result("eight-actors-hot", types, sum(tickNanos),
                Map.of("simulatedTicks", simulatedTicks,
                        "actors", 8,
                        "p50TickNanos", percentile(tickNanos, 0.50),
                        "p95TickNanos", percentile(tickNanos, 0.95),
                        "p99TickNanos", percentile(tickNanos, 0.99),
                        "maxTickNanos", tickNanos[tickNanos.length - 1]), counters);
    }

    private static Map<String, Object> runHeavyNbtScenario(int types) {
        StorageMetrics.reset();
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        long started = System.nanoTime();
        for (int index = 0; index < types; index++) {
            runtime.insert(heavyVariant(index), 16,
                    TYPE_CAPACITY, ITEM_CAPACITY);
        }
        StorageTickCoordinator.flush();
        long buildNanos = System.nanoTime() - started;
        long snapshotNanos = time(() -> assertEquals(types,
                runtime.snapshotEntries().size()));
        assertEquals(types, runtime.storedTypes());
        return result("heavy-nbt", types, buildNanos,
                Map.of("snapshotNanos", snapshotNanos,
                        "estimatedBytes", runtime.estimatedBytes()),
                StorageMetrics.snapshot());
    }

    private static Map<String, Object> runRandomizedModelScenario(
            int types, int operations, long seed) {
        StorageMetrics.reset();
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        ItemStack[] templates = new ItemStack[types];
        long[] expected = new long[types];
        for (int index = 0; index < types; index++) {
            templates[index] = variant(index);
            expected[index] = 128;
            runtime.insert(templates[index], expected[index],
                    TYPE_CAPACITY, ITEM_CAPACITY);
        }
        StorageTickCoordinator.flush();
        // The randomized phase is the measured workload. Exclude the one
        // initialization batch so the expected batching invariant is exact.
        StorageMetrics.reset();
        Random random = new Random(seed);
        long started = System.nanoTime();
        for (int operation = 0; operation < operations; operation++) {
            int index = random.nextInt(types);
            int amount = 1 + random.nextInt(8);
            if (random.nextBoolean() || expected[index] <= 16) {
                long inserted = runtime.insert(templates[index], amount,
                        TYPE_CAPACITY, ITEM_CAPACITY);
                expected[index] += inserted;
            } else {
                ItemStack extracted = runtime.extract(index + 1L, amount);
                expected[index] -= extracted.getCount();
            }
            if ((operation + 1) % 100 == 0) {
                StorageTickCoordinator.flush();
            }
        }
        StorageTickCoordinator.flush();
        long elapsed = System.nanoTime() - started;

        List<StorageRuntime.SnapshotEntry> snapshot = runtime.snapshotEntries();
        assertEquals(types, snapshot.size());
        long expectedTotal = 0;
        for (int index = 0; index < types; index++) {
            assertEquals(index, snapshot.get(index).stack().getTag()
                    .getInt("TriStorageStressVariant"));
            assertEquals(expected[index], snapshot.get(index).count());
            expectedTotal += expected[index];
        }
        assertEquals(expectedTotal, runtime.totalItems());
        Map<String, Long> counters = StorageMetrics.snapshot();
        assertEquals(1_000L, counters.getOrDefault("mutation_batches", 0L));
        assertEquals(0L, counters.getOrDefault("full_sorts", 0L));
        assertEquals(0L, counters.getOrDefault("full_scans", 0L));
        return result("randomized-model", types, elapsed,
                Map.of("operations", operations,
                        "seed", seed,
                        "finalItems", expectedTotal), counters);
    }

    private static Map<String, Object> runJournalScenario(int frames) throws Exception {
        StorageMetrics.reset();
        Path directory = Files.createTempDirectory("tristorage-journal-stress-");
        Path journal = directory.resolve("stress.journal");
        Method append = StorageRepository.class.getDeclaredMethod(
                "appendFrame", Path.class, CompoundTag.class, boolean.class);
        Method read = StorageRepository.class.getDeclaredMethod("readFrames", Path.class);
        append.setAccessible(true);
        read.setAccessible(true);
        long writeStarted = System.nanoTime();
        for (int index = 0; index < frames; index++) {
            CompoundTag frame = new CompoundTag();
            frame.putLong("Revision", index + 1L);
            ListTag operations = new ListTag();
            CompoundTag operation = new CompoundTag();
            operation.putLong("EntryId", index + 1L);
            operation.put("Stack", variant(index).save(new CompoundTag()));
            operation.putLong("Count", 64);
            operations.add(operation);
            frame.put("Operations", operations);
            append.invoke(null, journal, frame, false);
        }
        long writeNanos = System.nanoTime() - writeStarted;
        long readStarted = System.nanoTime();
        @SuppressWarnings("unchecked")
        List<CompoundTag> recovered = (List<CompoundTag>) read.invoke(null, journal);
        long readNanos = System.nanoTime() - readStarted;
        assertEquals(frames, recovered.size());
        return result("journal", frames, writeNanos,
                Map.of("readNanos", readNanos,
                        "fileBytes", Files.size(journal)), StorageMetrics.snapshot());
    }

    private static ItemStack variant(int index) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putInt("TriStorageStressVariant", index);
        return stack;
    }

    private static ItemStack heavyVariant(int index) {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        CompoundTag root = stack.getOrCreateTag();
        root.putInt("TriStorageStressVariant", index);
        CompoundTag blockEntity = new CompoundTag();
        ListTag items = new ListTag();
        for (int slot = 0; slot < 27; slot++) {
            CompoundTag item = new CompoundTag();
            item.putByte("Slot", (byte) slot);
            item.putString("id", slot % 2 == 0
                    ? "minecraft:stone" : "minecraft:iron_ingot");
            item.putByte("Count", (byte) (1 + slot % 4));
            items.add(item);
        }
        blockEntity.put("Items", items);
        root.put("BlockEntityTag", blockEntity);
        return stack;
    }

    private static TerminalFilter.Selection all() {
        return TerminalFilter.sanitize("", TerminalFilter.CategoryMode.NONE,
                TerminalFilter.ALL);
    }

    private static long time(Runnable operation) {
        long started = System.nanoTime();
        operation.run();
        return System.nanoTime() - started;
    }

    private static long sum(long[] values) {
        long result = 0;
        for (long value : values) {
            result += value;
        }
        return result;
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static Map<String, Object> result(String scenario, int types,
                                               long elapsedNanos,
                                               Map<String, ?> details,
                                               Map<String, Long> counters) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", scenario);
        result.put("types", types);
        result.put("elapsedNanos", elapsedNanos);
        result.put("details", details);
        result.put("counters", counters);
        return result;
    }

    private static void writeReport(List<Map<String, Object>> results) throws Exception {
        Path output = Path.of(System.getProperty("tristorage.stress.output",
                "build/reports/tristorage-stress/benchmark.json"));
        Files.createDirectories(output.getParent());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("java", System.getProperty("java.version"));
        report.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("results", results);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
