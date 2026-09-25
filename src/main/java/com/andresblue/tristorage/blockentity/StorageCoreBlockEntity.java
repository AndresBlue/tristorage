package com.andresblue.tristorage.blockentity;

import com.andresblue.tristorage.version.Nbt;
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
import com.andresblue.tristorage.version.SavedStacks;
import com.andresblue.tristorage.version.Stacks;
import org.jetbrains.annotations.Nullable;
//? if >=1.20.5 {
/*import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
*///?}
//? if >=1.21.6 {
/*import com.andresblue.tristorage.version.ValueTags;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
    /** Every key this block entity saves; the orbit keys are what clients receive. */
    private static final String[] SAVED_KEYS = {
            STORAGE_ID_KEY, PortableCoreData.OWNERSHIP_TOKEN_KEY,
            PortableCoreData.FORMAT_VERSION_KEY, PortableCoreData.DATA_VERSION_KEY,
            REVISION_KEY, PortableCoreData.CHESTS_KEY, PortableCoreData.ENTRIES_KEY,
            PortableCoreData.TYPES_SUMMARY_KEY, PortableCoreData.ITEMS_SUMMARY_KEY,
            ORBIT_ENTRIES_KEY, ORBIT_REVISION_KEY};
    //? if >=1.20.5 {
    /*private static final List<DataComponentType<?>> ORBIT_VISUAL_COMPONENTS = List.of(
            DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS,
            DataComponents.POTION_CONTENTS, DataComponents.DYED_COLOR,
            DataComponents.CUSTOM_MODEL_DATA, DataComponents.PROFILE, DataComponents.TRIM,
            DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
    *///?} else {
    private static final List<String> ORBIT_VISUAL_KEYS = List.of(
            "Enchantments", "StoredEnchantments", "Potion", "CustomPotionColor",
            "CustomModelData", "SkullOwner", "Trim");
    //?}

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
    private int pendingLegacyDataVersion;
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
                    CompoundTag stored = Nbt.getCompound(pendingLegacyEntries, pendingLegacyIndex++);
                    if (legacyNeedsIdentity) {
                        legacyChecksum = 31 * legacyChecksum + stored.hashCode();
                    }
                    ItemStack stack = SavedStacks.parse(Nbt.getCompound(stored, "Stack"),
                            pendingLegacyDataVersion, serverWorld.registryAccess());
                    long count = Math.max(0, Nbt.getLong(stored, "Count"));
                    long entryId = Nbt.hasNumber(stored, ENTRY_ID_KEY)
                            ? Math.max(1, Nbt.getLong(stored, ENTRY_ID_KEY))
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
        if (level != null && level.isClientSide() && clientOrbitSynced) {
            return clientOrbitItems.stream().map(ItemStack::copy).toList();
        }
        return serverOrbitSnapshot().stream().map(ItemStack::copy).toList();
    }

    public long orbitSnapshotRevision() {
        return level != null && level.isClientSide() && clientOrbitSynced
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

    /** The block entity data a portable Core item carries. */
    public CompoundTag portableData() {
        //? if >=1.20.5 {
        /*return saveWithoutMetadata(level.registryAccess());
        *///?} else {
        return saveWithoutMetadata();
        //?}
    }

    //? if >=1.20.5 {
    /*// Loot tables copy this component onto the dropped Core (see copy_components).
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        CompoundTag portable = PortableCoreData.sanitizedCopy(portableData());
        if (portable != null) {
            Stacks.putBlockEntityData(components, getType(), portable);
        }
    }
    *///?}

    //? if >=1.21.6 {
    /*@Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag nbt = new CompoundTag();
        writeCore(nbt, level != null ? level.registryAccess() : RegistryAccess.EMPTY);
        ValueTags.write(output, nbt);
    }
    *///?} elif >=1.20.5 {
    /*@Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        writeCore(nbt, registries);
    }
    *///?} else {
    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        writeCore(nbt, null);
    }
    //?}

    private void writeCore(CompoundTag nbt, HolderLookup.Provider registries) {
        activeRuntime();
        if (!legacyNeedsIdentity) {
            nbt.putString(STORAGE_ID_KEY, storageId().toString());
            nbt.putString(PortableCoreData.OWNERSHIP_TOKEN_KEY, ownershipToken);
        }
        nbt.putInt(PortableCoreData.FORMAT_VERSION_KEY, 1);
        int dataVersion = pendingLegacyEntries == null
                ? SavedStacks.currentVersion() : pendingLegacyDataVersion;
        if (dataVersion > 0) {
            nbt.putInt(PortableCoreData.DATA_VERSION_KEY, dataVersion);
        }
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
                stored.put("Stack", Stacks.save(entry.stack(), registries));
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

    private CompoundTag writeOrbitNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        nbt.putLong(ORBIT_REVISION_KEY, revision());
        ListTag list = new ListTag();
        for (ItemStack template : serverOrbitSnapshot()) {
            CompoundTag stored = new CompoundTag();
            stored.put("Stack", Stacks.save(orbitDisplayStack(template), registries));
            list.add(stored);
        }
        nbt.put(ORBIT_ENTRIES_KEY, list);
        return nbt;
    }

    /**
     * Orbit stacks are sent to every nearby player in chunk and update
     * packets. Only data that changes how the item renders is kept, so
     * shulker contents or book pages can neither overflow the chunk packet nor
     * reveal what the storage holds.
     */
    //? if >=1.20.5 {
    /*static ItemStack orbitDisplayStack(ItemStack template) {
        ItemStack display = new ItemStack(template.getItem());
        for (DataComponentType<?> type : ORBIT_VISUAL_COMPONENTS) {
            copyComponent(template, display, type);
        }
        return display;
    }

    private static <T> void copyComponent(ItemStack from, ItemStack to, DataComponentType<T> type) {
        T value = from.get(type);
        if (value != null) {
            to.set(type, value);
        }
    }
    *///?} else {
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
        CompoundTag sourceDisplay = Nbt.getCompound(source, "display");
        if (Nbt.hasNumber(sourceDisplay, "color")) {
            CompoundTag keptDisplay = new CompoundTag();
            keptDisplay.putInt("color", Nbt.getInt(sourceDisplay, "color"));
            kept.put("display", keptDisplay);
        }
        if (!kept.isEmpty()) {
            display.setTag(kept);
        }
        return display;
    }
    //?}

    //? if >=1.20.5 {
    /*@Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
    *///?} else {
    @Override
    public CompoundTag getUpdateTag() {
        HolderLookup.Provider registries = null;
    //?}
        CompoundTag nbt = new CompoundTag();
        ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(getType());
        if (id != null) {
            nbt.putString("id", id.toString());
        }
        nbt.putInt("x", worldPosition.getX());
        nbt.putInt("y", worldPosition.getY());
        nbt.putInt("z", worldPosition.getZ());
        nbt.merge(writeOrbitNbt(registries));
        return nbt;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        //? if >=1.20.5 {
        /*return ClientboundBlockEntityDataPacket.create(this,
                (blockEntity, registries) -> writeOrbitNbt(registries));
        *///?} else {
        return ClientboundBlockEntityDataPacket.create(this, ignored -> writeOrbitNbt(null));
        //?}
    }

    //? if >=1.21.6 {
    /*@Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        readCore(ValueTags.read(input, SAVED_KEYS), input.lookup());
    }
    *///?} elif >=1.20.5 {
    /*@Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        readCore(nbt, registries);
    }
    *///?} else {
    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        readCore(nbt, null);
    }
    //?}

    private void readCore(CompoundTag nbt, HolderLookup.Provider registries) {
        if (!Nbt.hasList(nbt, PortableCoreData.ENTRIES_KEY)
                && Nbt.hasList(nbt, ORBIT_ENTRIES_KEY)) {
            readOrbitNbt(nbt, registries);
            return;
        }
        boolean hasStorageId = Nbt.hasString(nbt, STORAGE_ID_KEY);
        StorageId id;
        try {
            id = hasStorageId ? StorageId.parse(Nbt.getString(nbt, STORAGE_ID_KEY))
                    : StorageId.random();
        } catch (IllegalArgumentException invalidId) {
            id = StorageId.random();
            hasStorageId = false;
        }
        boolean hasOwnershipToken = Nbt.hasString(nbt, PortableCoreData.OWNERSHIP_TOKEN_KEY);
        ownershipToken = hasOwnershipToken
                ? Nbt.getString(nbt, PortableCoreData.OWNERSHIP_TOKEN_KEY)
                : UUID.randomUUID().toString();
        StorageRuntime loaded = new StorageRuntime(id);
        loaded.loadInstalledChests(Math.min(Math.max(0,
                Nbt.getInt(nbt, PortableCoreData.CHESTS_KEY)), maxChests()));
        ListTag list = Nbt.getCompoundList(nbt, PortableCoreData.ENTRIES_KEY);
        legacyNeedsIdentity = !hasStorageId && (list.size() > 0
                || loaded.installedChests() > 0);
        boolean requiresStagedLoad = !hasStorageId || !list.isEmpty();
        if (requiresStagedLoad) {
            loaded.beginLoading();
            pendingLegacyEntries = list;
            pendingLegacyIndex = 0;
            pendingLegacyFallbackId = 1;
            pendingLegacyRevision = Math.max(0, Nbt.getLong(nbt, REVISION_KEY));
            pendingLegacyTypeSummary = Math.max(list.size(),
                    Nbt.getInt(nbt, PortableCoreData.TYPES_SUMMARY_KEY));
            pendingLegacyItemSummary = Math.max(0,
                    Nbt.getLong(nbt, PortableCoreData.ITEMS_SUMMARY_KEY));
            pendingLegacyDataVersion = Nbt.getInt(nbt, PortableCoreData.DATA_VERSION_KEY);
            legacyChecksum = 31 + loaded.installedChests();
            legacyLoadTask = null;
        } else {
            pendingLegacyEntries = null;
            loaded.finishLoading(Math.max(0, Nbt.getLong(nbt, REVISION_KEY)));
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

    private void readOrbitNbt(CompoundTag nbt, HolderLookup.Provider registries) {
        clientOrbitItems.clear();
        ListTag list = Nbt.getCompoundList(nbt, ORBIT_ENTRIES_KEY);
        for (int index = 0; index < list.size() && index < ORBIT_ENTRY_LIMIT; index++) {
            ItemStack stack = Stacks.parse(Nbt.getCompound(Nbt.getCompound(list, index), "Stack"),
                    registries);
            if (!stack.isEmpty()) {
                stack.setCount(1);
                clientOrbitItems.add(stack);
            }
        }
        clientOrbitRevision = Nbt.getLong(nbt, ORBIT_REVISION_KEY);
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
