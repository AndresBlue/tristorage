package com.andresblue.tristorage.blockentity;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.StorageCoreBlock;
import com.andresblue.tristorage.item.StorageCoreBlockItem;
import com.andresblue.tristorage.screen.CoreScreenHandler;
import com.andresblue.tristorage.storage.PortableCoreData;
import com.andresblue.tristorage.storage.StorageId;
import com.andresblue.tristorage.storage.StorageMetrics;
import com.andresblue.tristorage.storage.StorageRuntime;
import com.andresblue.tristorage.storage.StorageRepositories;
import com.andresblue.tristorage.storage.StorageRepository;
import com.andresblue.tristorage.storage.StorageTickCoordinator;
import com.andresblue.tristorage.storage.StorageTier;
import com.andresblue.tristorage.storage.TerminalFilter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/**
 * Lightweight world anchor for a storage. Content and indexes live in a
 * StorageRuntime, so normal operations never rebuild sorted snapshots or SNBT
 * keys.
 */
public final class StorageCoreBlockEntity extends BlockEntity implements MenuProvider {
    public static final String STORAGE_ID_KEY = "TriStorageId";
    private static final String ENTRY_ID_KEY = "EntryId";
    private static final String REVISION_KEY = "StorageRevision";
    private static final String ORBIT_ENTRIES_KEY = "TriStorageOrbitEntries";
    private static final String ORBIT_REVISION_KEY = "TriStorageOrbitRevision";
    private static final int ORBIT_ENTRY_LIMIT = 24;
    private static final List<String> ORBIT_VISUAL_KEYS = List.of(
            "Enchantments", "StoredEnchantments", "Potion", "CustomPotionColor",
            "CustomModelData", "SkullOwner", "Trim");

    private StorageRuntime runtime;
    private final StorageRuntime.Listener anchorListener = this::onRuntimeFlush;
    private final List<ItemStack> clientOrbitItems = new ArrayList<>();
    private List<ItemStack> serverOrbitItems = List.of();
    private boolean serverOrbitDirty = true;
    private boolean clientOrbitSynced;
    private long clientOrbitRevision = Long.MIN_VALUE;
    private boolean repositoryAttached;
    private boolean repositoryDurable;
    private boolean recoveryRequired;
    private boolean portablePrepared;

    private void onRuntimeFlush(StorageRuntime ignored, StorageRuntime.ChangeSet changes) {
        // Repository-backed catalogs are journaled independently. Dirtying the
        // chunk for every item movement only causes vanilla to serialize a
        // redundant BlockEntity summary during its next save pass.
        if (!repositoryDurable) {
            setChanged();
        }
        if (changes.structural()) {
            serverOrbitDirty = true;
            syncOrbitToClients();
        }
    }
    private String ownershipToken = UUID.randomUUID().toString();
    private boolean legacyNeedsIdentity;
    private int legacyChecksum;
    private ListTag pendingLegacyEntries;
    private int pendingLegacyIndex;
    private long pendingLegacyFallbackId = 1;
    private long pendingLegacyRevision;
    private int pendingLegacyTypeSummary;
    private long pendingLegacyItemSummary;
    private StorageTickCoordinator.BoundedTask legacyLoadTask;

    public StorageCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TriStorageMod.STORAGE_CORE_BLOCK_ENTITY, pos, state);
        replaceRuntime(new StorageRuntime(StorageId.random()));
    }

    private void replaceRuntime(StorageRuntime replacement) {
        if (runtime != null) {
            runtime.removeListener(anchorListener);
        }
        runtime = replacement;
        runtime.addListener(anchorListener);
    }

    private StorageRuntime activeRuntime() {
        if (pendingLegacyEntries != null) {
            scheduleLegacyLoad();
            return runtime;
        }
        if (!repositoryAttached && level instanceof ServerLevel serverWorld) {
            repositoryAttached = true;
            StorageRepository.Attachment attachment = StorageRepositories
                    .get(serverWorld.getServer()).attach(runtime, ownershipToken,
                    serverWorld, worldPosition, tier(), () -> {
                        repositoryDurable = true;
                        setChanged();
                    });
            StorageRuntime shared = attachment.runtime();
            ownershipToken = attachment.ownershipToken();
            recoveryRequired = !attachment.valid();
            if (shared != runtime) {
                replaceRuntime(shared);
            }
            if (recoveryRequired) {
                runtime.beginLoading();
            }
        }
        return runtime;
    }

    private void scheduleLegacyLoad() {
        if (legacyLoadTask != null || !(level instanceof ServerLevel)) {
            return;
        }
        legacyLoadTask = new StorageTickCoordinator.BoundedTask() {
            private boolean complete;

            @Override
            public int step(int budget) {
                if (isRemoved() || !(level instanceof ServerLevel serverWorld)) {
                    complete = true;
                    return 0;
                }
                int start = pendingLegacyIndex;
                int end = Math.min(pendingLegacyEntries.size(), start + budget);
                while (pendingLegacyIndex < end) {
                    CompoundTag stored = pendingLegacyEntries.getCompound(pendingLegacyIndex++);
                    if (legacyNeedsIdentity) {
                        legacyChecksum = 31 * legacyChecksum + stored.hashCode();
                    }
                    ItemStack stack = ItemStack.of(stored.getCompound("Stack"));
                    long count = Math.max(0, stored.getLong("Count"));
                    long entryId = stored.contains(ENTRY_ID_KEY, Tag.TAG_ANY_NUMERIC)
                            ? Math.max(1, stored.getLong(ENTRY_ID_KEY))
                            : pendingLegacyFallbackId;
                    pendingLegacyFallbackId = Math.max(
                            pendingLegacyFallbackId + 1, entryId + 1);
                    runtime.loadEntry(entryId, stack, count);
                }
                if (pendingLegacyIndex >= pendingLegacyEntries.size()) {
                    if (legacyNeedsIdentity) {
                        StorageId deterministic = StorageId.deterministic(
                                serverWorld.dimension().location() + ":" + worldPosition.asLong()
                                        + ":" + legacyChecksum + ":" + tier().level());
                        runtime.assignIdBeforeReady(deterministic);
                        ownershipToken = StorageId.deterministic(
                                "token:" + deterministic).toString();
                    }
                    runtime.finishLoading(pendingLegacyRevision);
                    pendingLegacyEntries = null;
                    legacyNeedsIdentity = false;
                    complete = true;
                    legacyLoadTask = null;
                    StorageMetrics.increment("legacy.loads_completed");
                    activeRuntime();
                }
                return pendingLegacyIndex - start;
            }

            @Override
            public boolean complete() {
                return complete;
            }
        };
        StorageTickCoordinator.schedule(legacyLoadTask);
    }

    public StorageId storageId() {
        return runtime.id();
    }

    public StorageRuntime runtime() {
        return activeRuntime();
    }

    public boolean isRecoveryRequired() {
        activeRuntime();
        return recoveryRequired;
    }

    public boolean preparePortable() {
        if (portablePrepared) {
            return true;
        }
        activeRuntime();
        if (recoveryRequired || !runtime.isReady()
                || !(level instanceof ServerLevel serverWorld)) {
            return false;
        }
        StorageTickCoordinator.flush();
        StorageRepository.PortablePreparation preparation = StorageRepositories
                .get(serverWorld.getServer())
                .preparePortable(storageId(), ownershipToken, serverWorld, worldPosition);
        PortableDecision decision = interpretPortablePreparation(preparation);
        if (!decision.prepared()) {
            // This is the ordinary path immediately after mutations: keep the
            // active Core healthy while the journal is forced asynchronously.
            if (decision.recoveryRequired()) {
                recoveryRequired = true;
            }
            return false;
        }
        ownershipToken = decision.token();
        portablePrepared = true;
        setChanged();
        return true;
    }

    static PortableDecision interpretPortablePreparation(
            StorageRepository.PortablePreparation preparation) {
        return switch (preparation.status()) {
            case READY -> new PortableDecision(true, false, preparation.token());
            case PENDING -> new PortableDecision(false, false, "");
            case INVALID -> new PortableDecision(false, true, "");
        };
    }

    public void cancelPortablePreparation() {
        if (!portablePrepared || !(level instanceof ServerLevel serverWorld)) {
            return;
        }
        String rotated = StorageRepositories.get(serverWorld.getServer())
                .cancelPortable(storageId(), ownershipToken, serverWorld, worldPosition);
        if (rotated != null) {
            ownershipToken = rotated;
            portablePrepared = false;
            setChanged();
        }
    }

    public StorageTier tier() {
        return getBlockState().getBlock() instanceof StorageCoreBlock core
                ? core.tier() : StorageTier.IRON;
    }

    public int installedChests() {
        return activeRuntime().installedChests();
    }

    public int maxChests() {
        return tier().chestCapacity();
    }

    public int typeCapacity() {
        return tier().typeCapacity(installedChests());
    }

    public long itemCapacity() {
        return tier().itemCapacity(installedChests());
    }

    public int storedTypes() {
        return activeRuntime().storedTypes();
    }

    public long totalItems() {
        return activeRuntime().totalItems();
    }

    public long revision() {
        return activeRuntime().revision();
    }

    public long stateEpoch() {
        return activeRuntime().stateEpoch();
    }

    public int addChests(int requested) {
        if (!activeRuntime().isReady() || recoveryRequired) {
            return 0;
        }
        int accepted = Math.min(Math.max(0, requested), maxChests() - installedChests());
        if (accepted > 0) {
            activeRuntime().setInstalledChests(installedChests() + accepted);
        }
        return accepted;
    }

    public int removableChests(int requested) {
        int removable = Math.min(Math.max(0, requested), installedChests());
        while (removable > 0) {
            int remaining = installedChests() - removable;
            if (storedTypes() <= tier().typeCapacity(remaining)
                    && totalItems() <= tier().itemCapacity(remaining)) {
                return removable;
            }
            removable--;
        }
        return 0;
    }

    public int removeChests(int requested) {
        if (!activeRuntime().isReady() || recoveryRequired) {
            return 0;
        }
        int removed = removableChests(requested);
        if (removed > 0) {
            activeRuntime().setInstalledChests(installedChests() - removed);
        }
        return removed;
    }

    public long insert(ItemStack source, long requested) {
        if (source.isEmpty() || source.getItem() instanceof StorageCoreBlockItem
                || requested <= 0 || installedChests() <= 0) {
            return 0;
        }
        return activeRuntime().insert(source, requested, typeCapacity(), itemCapacity());
    }

    public ItemStack extract(long entryId, int requested) {
        return activeRuntime().extract(entryId, requested);
    }

    public ItemStack extractMatching(ItemStack template, int requested) {
        return activeRuntime().extractMatching(template, requested);
    }

    public ItemStack stackTemplate(long entryId) {
        StorageRuntime.SnapshotEntry entry = activeRuntime().snapshotEntry(entryId);
        return entry == null ? ItemStack.EMPTY : entry.stack();
    }

    public List<StorageRuntime.SnapshotEntry> matchingEntries(Ingredient ingredient) {
        return activeRuntime().matchingEntries(ingredient, 9);
    }

    public List<StorageRuntime.SnapshotEntry> matchingRecipeEntries(
            List<Ingredient> ingredients) {
        return activeRuntime().matchingAnyEntries(ingredients, 9);
    }

    /** Runtime batching is global and completes at END_SERVER_TICK. */
    public <T> T batchMutations(Supplier<T> operation) {
        return operation.get();
    }

    public void batchMutations(Runnable operation) {
        operation.run();
    }

    public List<EntryView> page(int page, int pageSize) {
        return page(page, pageSize, EntryOrder.INSERTION);
    }

    public List<EntryView> page(int page, int pageSize, EntryOrder order) {
        return browse(page, pageSize, order,
                TerminalFilter.sanitize("", TerminalFilter.CategoryMode.NONE, TerminalFilter.ALL)).entries();
    }

    public int pageCount(int pageSize) {
        return Math.max(1, (storedTypes() + pageSize - 1) / pageSize);
    }

    public BrowseResult browse(int page, int pageSize, EntryOrder order,
                               TerminalFilter.Selection filter) {
        StorageRuntime.BrowseResult result = browseRuntime(page, pageSize, order, filter);
        List<EntryView> entries = result.entries().stream()
                .map(entry -> new EntryView(entry.id(), entry.stack(), entry.count()))
                .toList();
        return new BrowseResult(entries, result.page(), result.pageCount(),
                result.filteredTypes(), result.filteredItems(), result.categories(),
                result.selectedCategory(), result.revision());
    }

    public StorageRuntime.BrowseResult browseRuntime(
            int page, int pageSize, EntryOrder order,
            TerminalFilter.Selection filter) {
        return activeRuntime().browse(page, pageSize, switch (order) {
            case REGISTRY -> StorageRuntime.EntryOrder.REGISTRY;
            case COUNT -> StorageRuntime.EntryOrder.COUNT;
            case RECENT -> StorageRuntime.EntryOrder.RECENT;
            case INSERTION -> StorageRuntime.EntryOrder.INSERTION;
        }, filter);
    }

    public StorageRuntime.ViewLease retainView(EntryOrder order,
                                               TerminalFilter.Selection filter) {
        return activeRuntime().retainView(switch (order) {
            case REGISTRY -> StorageRuntime.EntryOrder.REGISTRY;
            case COUNT -> StorageRuntime.EntryOrder.COUNT;
            case RECENT -> StorageRuntime.EntryOrder.RECENT;
            case INSERTION -> StorageRuntime.EntryOrder.INSERTION;
        }, filter);
    }

    public List<ItemStack> orbitItemSnapshot() {
        if (level != null && level.isClientSide && clientOrbitSynced) {
            return clientOrbitItems.stream().map(ItemStack::copy).toList();
        }
        return serverOrbitSnapshot().stream().map(ItemStack::copy).toList();
    }

    public long orbitSnapshotRevision() {
        return level != null && level.isClientSide && clientOrbitSynced
                ? clientOrbitRevision : revision();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.tristorage.core", tier().level());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        return new CoreScreenHandler(syncId, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        activeRuntime();
        if (!legacyNeedsIdentity) {
            nbt.putString(STORAGE_ID_KEY, storageId().toString());
            nbt.putString(PortableCoreData.OWNERSHIP_TOKEN_KEY, ownershipToken);
        }
        nbt.putInt(PortableCoreData.FORMAT_VERSION_KEY, 1);
        nbt.putLong(REVISION_KEY, pendingLegacyEntries == null
                ? revision() : pendingLegacyRevision);
        nbt.putInt(PortableCoreData.CHESTS_KEY, installedChests());
        ListTag list = new ListTag();
        if (pendingLegacyEntries != null) {
            list = pendingLegacyEntries;
        } else if (!repositoryDurable) {
            for (StorageRuntime.SnapshotEntry entry : runtime.snapshotEntries()) {
                CompoundTag stored = new CompoundTag();
                stored.putLong(ENTRY_ID_KEY, entry.id());
                stored.put("Stack", entry.stack().save(new CompoundTag()));
                stored.putLong("Count", entry.count());
                list.add(stored);
            }
        }
        nbt.put(PortableCoreData.ENTRIES_KEY, list);
        int typeSummary = pendingLegacyEntries == null
                ? runtime.storedTypes()
                : Math.max(runtime.storedTypes(), pendingLegacyTypeSummary);
        long itemSummary = pendingLegacyEntries == null
                ? runtime.totalItems()
                : Math.max(runtime.totalItems(), pendingLegacyItemSummary);
        nbt.putInt(PortableCoreData.TYPES_SUMMARY_KEY, typeSummary);
        nbt.putLong(PortableCoreData.ITEMS_SUMMARY_KEY, itemSummary);
    }

    private CompoundTag writeOrbitNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putLong(ORBIT_REVISION_KEY, revision());
        ListTag list = new ListTag();
        for (ItemStack template : serverOrbitSnapshot()) {
            CompoundTag stored = new CompoundTag();
            stored.put("Stack", orbitDisplayStack(template).save(new CompoundTag()));
            list.add(stored);
        }
        nbt.put(ORBIT_ENTRIES_KEY, list);
        return nbt;
    }

    /**
     * Orbit stacks are sent to every nearby player in chunk and update
     * packets. Only tags that change how the item renders are kept, so
     * shulker contents or book pages can neither overflow the chunk packet nor
     * reveal what the storage holds.
     */
    static ItemStack orbitDisplayStack(ItemStack template) {
        ItemStack display = new ItemStack(template.getItem());
        CompoundTag source = template.getTag();
        if (source == null) {
            return display;
        }
        CompoundTag kept = new CompoundTag();
        for (String key : ORBIT_VISUAL_KEYS) {
            Tag value = source.get(key);
            if (value != null) {
                kept.put(key, value.copy());
            }
        }
        CompoundTag sourceDisplay = source.getCompound("display");
        if (sourceDisplay.contains("color", Tag.TAG_ANY_NUMERIC)) {
            CompoundTag keptDisplay = new CompoundTag();
            keptDisplay.putInt("color", sourceDisplay.getInt("color"));
            kept.put("display", keptDisplay);
        }
        if (!kept.isEmpty()) {
            display.setTag(kept);
        }
        return display;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag nbt = new CompoundTag();
        ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(getType());
        if (id != null) {
            nbt.putString("id", id.toString());
        }
        nbt.putInt("x", worldPosition.getX());
        nbt.putInt("y", worldPosition.getY());
        nbt.putInt("z", worldPosition.getZ());
        nbt.merge(writeOrbitNbt());
        return nbt;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, ignored -> writeOrbitNbt());
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (!nbt.contains(PortableCoreData.ENTRIES_KEY, Tag.TAG_LIST)
                && nbt.contains(ORBIT_ENTRIES_KEY, Tag.TAG_LIST)) {
            readOrbitNbt(nbt);
            return;
        }
        boolean hasStorageId = nbt.contains(STORAGE_ID_KEY, Tag.TAG_STRING);
        StorageId id;
        try {
            id = hasStorageId ? StorageId.parse(nbt.getString(STORAGE_ID_KEY))
                    : StorageId.random();
        } catch (IllegalArgumentException invalidId) {
            id = StorageId.random();
            hasStorageId = false;
        }
        boolean hasOwnershipToken = nbt.contains(PortableCoreData.OWNERSHIP_TOKEN_KEY,
                Tag.TAG_STRING);
        ownershipToken = hasOwnershipToken
                ? nbt.getString(PortableCoreData.OWNERSHIP_TOKEN_KEY)
                : UUID.randomUUID().toString();
        StorageRuntime loaded = new StorageRuntime(id);
        loaded.loadInstalledChests(Math.min(Math.max(0,
                nbt.getInt(PortableCoreData.CHESTS_KEY)), maxChests()));
        ListTag list = nbt.getList(PortableCoreData.ENTRIES_KEY, Tag.TAG_COMPOUND);
        legacyNeedsIdentity = !hasStorageId && (list.size() > 0
                || loaded.installedChests() > 0);
        boolean requiresStagedLoad = !hasStorageId || !list.isEmpty();
        if (requiresStagedLoad) {
            loaded.beginLoading();
            pendingLegacyEntries = list;
            pendingLegacyIndex = 0;
            pendingLegacyFallbackId = 1;
            pendingLegacyRevision = Math.max(0, nbt.getLong(REVISION_KEY));
            pendingLegacyTypeSummary = Math.max(list.size(),
                    nbt.getInt(PortableCoreData.TYPES_SUMMARY_KEY));
            pendingLegacyItemSummary = Math.max(0,
                    nbt.getLong(PortableCoreData.ITEMS_SUMMARY_KEY));
            legacyChecksum = 31 + loaded.installedChests();
            legacyLoadTask = null;
        } else {
            pendingLegacyEntries = null;
            loaded.finishLoading(Math.max(0, nbt.getLong(REVISION_KEY)));
        }
        replaceRuntime(loaded);
        repositoryAttached = false;
        repositoryDurable = false;
        recoveryRequired = false;
        portablePrepared = false;
        clientOrbitSynced = false;
        clientOrbitItems.clear();
        serverOrbitItems = List.of();
        serverOrbitDirty = true;
    }

    private void readOrbitNbt(CompoundTag nbt) {
        clientOrbitItems.clear();
        ListTag list = nbt.getList(ORBIT_ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size() && index < ORBIT_ENTRY_LIMIT; index++) {
            ItemStack stack = ItemStack.of(list.getCompound(index).getCompound("Stack"));
            if (!stack.isEmpty()) {
                stack.setCount(1);
                clientOrbitItems.add(stack);
            }
        }
        clientOrbitRevision = nbt.getLong(ORBIT_REVISION_KEY);
        clientOrbitSynced = true;
    }

    private List<ItemStack> serverOrbitSnapshot() {
        if (serverOrbitDirty) {
            serverOrbitItems = activeRuntime().sampleTemplates(ORBIT_ENTRY_LIMIT);
            serverOrbitDirty = false;
        }
        return serverOrbitItems;
    }

    private void syncOrbitToClients() {
        if (level instanceof ServerLevel serverWorld) {
            serverWorld.getChunkSource().blockChanged(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        runtime.removeListener(anchorListener);
        if (repositoryAttached && level instanceof ServerLevel serverWorld) {
            StorageRepositories.get(serverWorld.getServer()).releaseAnchor(storageId());
        }
        super.setRemoved();
    }

    public enum EntryOrder {
        REGISTRY,
        COUNT,
        RECENT,
        INSERTION
    }

    public record EntryView(long id, ItemStack stack, long count) {
    }

    public record BrowseResult(List<EntryView> entries, int page, int pageCount,
                               int filteredTypes, long filteredItems,
                               List<String> categories, String selectedCategory,
                               long revision) {
    }

    record PortableDecision(boolean prepared, boolean recoveryRequired, String token) {
    }
}
