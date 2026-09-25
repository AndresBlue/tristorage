package com.andresblue.tristorage.blockentity;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.StorageCoreBlock;
import com.andresblue.tristorage.item.StorageCoreBlockItem;
import com.andresblue.tristorage.screen.CoreScreenHandler;
import com.andresblue.tristorage.storage.CoreStorageData;
import com.andresblue.tristorage.storage.StorageTier;
import com.andresblue.tristorage.storage.TerminalFilter;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class StorageCoreBlockEntity extends BlockEntity implements MenuProvider {
    public static final String CHESTS_KEY = "InstalledChests";
    public static final String ENTRIES_KEY = "Entries";
    private static final String ORBIT_ENTRIES_KEY = "TriStorageOrbitEntries";
    private static final String ORBIT_REVISION_KEY = "TriStorageOrbitRevision";
    private static final int ORBIT_ENTRY_LIMIT = 24;
    private final Map<String, StoredEntry> entries = new LinkedHashMap<>();
    private final Map<EntryOrder, List<String>> orderedKeyCache = new EnumMap<>(EntryOrder.class);
    private final Map<TerminalFilter.CategoryMode, List<String>> categoryCache =
            new EnumMap<>(TerminalFilter.CategoryMode.class);
    private final Map<BrowseCacheKey, FilteredIndex> filteredIndexCache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<BrowseCacheKey, FilteredIndex> eldest) {
                    return size() > 16;
                }
            };
    private int installedChests;
    private long totalItems;
    private int revision;
    private int seenCreativeGeneration = -1;
    private int mutationDepth;
    private boolean pendingMutation;
    private boolean pendingStructuralMutation;
    private final Map<String, Long> pendingAmountDeltas = new HashMap<>();
    private final List<ItemStack> clientOrbitItems = new ArrayList<>();
    private final List<ItemStack> serverOrbitItems = new ArrayList<>();
    private boolean serverOrbitDirty = true;
    private boolean clientOrbitSynced;
    private int clientOrbitRevision = Integer.MIN_VALUE;

    public StorageCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TriStorageMod.STORAGE_CORE_BLOCK_ENTITY.get(), pos, state);
    }

    public StorageTier tier() {
        if (getBlockState().getBlock() instanceof StorageCoreBlock core) {
            return core.tier();
        }
        return StorageTier.IRON;
    }

    public int installedChests() {
        return installedChests;
    }

    public int maxChests() {
        return tier().chestCapacity();
    }

    public int typeCapacity() {
        return tier().typeCapacity(installedChests);
    }

    public long itemCapacity() {
        return tier().itemCapacity(installedChests);
    }

    public int storedTypes() {
        return entries.size();
    }

    public long totalItems() {
        return totalItems;
    }

    public int revision() {
        return revision;
    }

    public int addChests(int requested) {
        int accepted = Math.min(Math.max(0, requested), maxChests() - installedChests);
        if (accepted > 0) {
            installedChests += accepted;
            metadataChanged();
        }
        return accepted;
    }

    public int removableChests(int requested) {
        int removable = Math.min(Math.max(0, requested), installedChests);
        while (removable > 0) {
            int remaining = installedChests - removable;
            if (entries.size() <= tier().typeCapacity(remaining)
                    && totalItems <= tier().itemCapacity(remaining)) {
                return removable;
            }
            removable--;
        }
        return 0;
    }

    public int removeChests(int requested) {
        int removed = removableChests(requested);
        if (removed > 0) {
            installedChests -= removed;
            metadataChanged();
        }
        return removed;
    }

    public long insert(ItemStack source, long requested) {
        if (source.isEmpty() || source.getItem() instanceof StorageCoreBlockItem
                || requested <= 0 || installedChests <= 0) {
            return 0;
        }
        String key = matchingKey(source);
        StoredEntry existing = key == null ? null : entries.get(key);
        if (existing == null && entries.size() >= typeCapacity()) {
            return 0;
        }
        long accepted = Math.min(requested, itemCapacity() - totalItems);
        if (accepted <= 0) {
            return 0;
        }
        boolean structural = existing == null;
        if (structural) {
            key = uniqueKey(source);
            entries.put(key, new StoredEntry(source.copyWithCount(1), accepted));
        } else {
            existing.count += accepted;
        }
        totalItems += accepted;
        contentChanged(structural, key, accepted);
        return accepted;
    }

    public ItemStack extract(String key, int requested) {
        StoredEntry existing = entries.get(key);
        if (existing == null || requested <= 0) {
            return ItemStack.EMPTY;
        }
        int extracted = (int) Math.min(Math.min(existing.count, requested), existing.template.getMaxStackSize());
        ItemStack result = existing.template.copyWithCount(extracted);
        existing.count -= extracted;
        totalItems -= extracted;
        boolean structural = existing.count <= 0;
        if (structural) {
            entries.remove(key);
        }
        contentChanged(structural, key, -extracted);
        return result;
    }

    /**
     * Coalesces many item mutations into one revision, one cache invalidation
     * and one chunk dirty mark. All callers run on Minecraft's server thread.
     */
    public <T> T batchMutations(Supplier<T> operation) {
        mutationDepth++;
        try {
            return operation.get();
        } finally {
            finishMutationBatch();
        }
    }

    public void batchMutations(Runnable operation) {
        batchMutations(() -> {
            operation.run();
            return null;
        });
    }

    public List<EntryView> page(int page, int pageSize) {
        return page(page, pageSize, EntryOrder.INSERTION);
    }

    public List<EntryView> page(int page, int pageSize, EntryOrder order) {
        int safePage = Math.max(0, page);
        int start = safePage * pageSize;
        if (start >= entries.size()) {
            return List.of();
        }
        List<String> orderedKeys = orderedKeyCache.computeIfAbsent(order, this::orderedKeys);
        int end = Math.min(start + pageSize, orderedKeys.size());
        List<EntryView> result = new ArrayList<>(end - start);
        for (int index = start; index < end; index++) {
            String key = orderedKeys.get(index);
            StoredEntry value = entries.get(key);
            if (value != null) {
                result.add(new EntryView(key, value.template.copy(), value.count));
            }
        }
        return result;
    }

    public int pageCount(int pageSize) {
        return Math.max(1, (entries.size() + pageSize - 1) / pageSize);
    }

    public BrowseResult browse(int requestedPage, int pageSize, EntryOrder order,
                               TerminalFilter.Selection requestedFilter) {
        ensureCreativeCategoriesCurrent();
        TerminalFilter.Selection filter = validatedFilter(requestedFilter);
        BrowseCacheKey cacheKey = new BrowseCacheKey(
                order, filter.query(), filter.mode(), filter.category());
        FilteredIndex index = filteredIndexCache.computeIfAbsent(
                cacheKey, ignored -> buildFilteredIndex(order, filter));
        int pageCount = Math.max(1, (index.keys().size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int start = page * pageSize;
        int end = Math.min(start + pageSize, index.keys().size());
        List<EntryView> visible = new ArrayList<>(Math.max(0, end - start));
        for (int position = start; position < end; position++) {
            String key = index.keys().get(position);
            StoredEntry entry = entries.get(key);
            if (entry != null) {
                visible.add(new EntryView(key, entry.template.copy(), entry.count));
            }
        }
        return new BrowseResult(
                visible, page, pageCount, index.keys().size(), index.totalItems(),
                availableCategories(filter.mode()), filter.category());
    }

    public Collection<EntryView> allEntries() {
        List<EntryView> result = new ArrayList<>(entries.size());
        entries.forEach((key, value) ->
                result.add(new EntryView(key, value.template.copy(), value.count)));
        return result;
    }

    /**
     * Returns the small client-side visual snapshot used by Linker renderers.
     * It deliberately contains templates only; storage counts remain private
     * to the server-side screen handler.
     */
    public List<ItemStack> orbitItemSnapshot() {
        if (level != null && level.isClientSide && clientOrbitSynced) {
            return clientOrbitItems.stream().map(ItemStack::copy).toList();
        }
        return serverOrbitSnapshot().stream().map(ItemStack::copy).toList();
    }

    public int orbitSnapshotRevision() {
        return level != null && level.isClientSide && clientOrbitSynced
                ? clientOrbitRevision : revision;
    }

    public CoreStorageData snapshot() {
        return new CoreStorageData(installedChests, entries.values().stream()
                .map(e -> new CoreStorageData.StoredStack(e.template, e.count)).toList());
    }

    public void writePortable(CompoundTag target, HolderLookup.Provider registries) {
        target.putInt(CHESTS_KEY, installedChests);
        ListTag list = new ListTag();
        for (StoredEntry entry : entries.values()) {
            CompoundTag stored = new CompoundTag();
            stored.put("Stack", entry.template.save(registries));
            stored.putLong("Count", entry.count);
            list.add(stored);
        }
        target.put(ENTRIES_KEY, list);
    }

    public void readPortable(CompoundTag source, HolderLookup.Provider registries) {
        installedChests = Math.min(Math.max(0, source.getInt(CHESTS_KEY)), maxChests());
        entries.clear();
        orderedKeyCache.clear();
        categoryCache.clear();
        filteredIndexCache.clear();
        totalItems = 0;
        ListTag list = source.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag stored = list.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, stored.getCompound("Stack"));
            long count = Math.max(0, stored.getLong("Count"));
            if (!stack.isEmpty() && count > 0) {
                stack.setCount(1);
                String key = matchingKey(stack);
                StoredEntry existing = key == null ? null : entries.get(key);
                if (existing == null) {
                    entries.put(uniqueKey(stack), new StoredEntry(stack, count));
                } else {
                    existing.count = Long.MAX_VALUE - existing.count < count
                            ? Long.MAX_VALUE : existing.count + count;
                }
                totalItems = Long.MAX_VALUE - totalItems < count
                        ? Long.MAX_VALUE : totalItems + count;
            }
        }
        revision++;
        clientOrbitSynced = false;
        clientOrbitItems.clear();
        serverOrbitDirty = true;
        serverOrbitItems.clear();
    }

    public void loadSnapshot(CoreStorageData data) {
        entries.clear();
        orderedKeyCache.clear();
        categoryCache.clear();
        filteredIndexCache.clear();
        totalItems = 0;
        installedChests = Math.min(Math.max(0, data.installedChests()), maxChests());
        for (CoreStorageData.StoredStack stored : data.entries()) {
            if (stored.stack().isEmpty() || stored.count() <= 0) {
                continue;
            }
            ItemStack stack = stored.stack().copyWithCount(1);
            String key = matchingKey(stack);
            StoredEntry existing = key == null ? null : entries.get(key);
            if (existing == null) {
                entries.put(uniqueKey(stack), new StoredEntry(stack, stored.count()));
            } else {
                existing.count = Long.MAX_VALUE - existing.count < stored.count()
                        ? Long.MAX_VALUE : existing.count + stored.count();
            }
            totalItems = Long.MAX_VALUE - totalItems < stored.count()
                    ? Long.MAX_VALUE : totalItems + stored.count();
        }
        revision++;
        serverOrbitDirty = true;
        serverOrbitItems.clear();
        setChanged();
        syncOrbitToClients();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.tristorage.core", tier().level());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new CoreScreenHandler(syncId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writePortable(tag, registries);
    }

    /** Sends only the visual orbit data to clients watching this core. */
    private CompoundTag writeOrbitNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt(ORBIT_REVISION_KEY, revision);
        ListTag list = new ListTag();
        for (ItemStack template : serverOrbitSnapshot()) {
            CompoundTag stored = new CompoundTag();
            stored.put("Stack", template.save(registries));
            list.add(stored);
        }
        nbt.put(ORBIT_ENTRIES_KEY, list);
        return nbt;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Keep chunk join packets small even for very large storage systems.
        // The full entries stay server-side; only a small representative visual
        // pool is needed by Linker renderers.
        return writeOrbitNbt(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this,
                (entity, registries) -> writeOrbitNbt(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains(ENTRIES_KEY, Tag.TAG_LIST)
                && tag.contains(ORBIT_ENTRIES_KEY, Tag.TAG_LIST)) {
            readOrbitNbt(tag, registries);
            return;
        }
        readPortable(tag, registries);
    }

    private void readOrbitNbt(CompoundTag tag, HolderLookup.Provider registries) {
        clientOrbitItems.clear();
        ListTag list = tag.getList(ORBIT_ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < ORBIT_ENTRY_LIMIT; i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i).getCompound("Stack"));
            if (!stack.isEmpty()) {
                stack.setCount(1);
                clientOrbitItems.add(stack);
            }
        }
        clientOrbitRevision = tag.getInt(ORBIT_REVISION_KEY);
        clientOrbitSynced = true;
    }

    private static String keyOf(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id + "|" + stack.getComponentsPatch();
    }

    private String matchingKey(ItemStack stack) {
        String directKey = keyOf(stack);
        StoredEntry direct = entries.get(directKey);
        return direct != null && ItemStack.isSameItemSameComponents(direct.template, stack)
                ? directKey : null;
    }

    private String uniqueKey(ItemStack stack) {
        String base = keyOf(stack);
        if (!entries.containsKey(base)) {
            return base;
        }
        int suffix = 2;
        while (entries.containsKey(base + "#" + suffix)) {
            suffix++;
        }
        return base + "#" + suffix;
    }

    private void metadataChanged() {
        revision++;
        setChanged();
        syncOrbitToClients();
    }

    private void contentChanged(boolean structural, String key, long amountDelta) {
        if (mutationDepth > 0) {
            pendingMutation = true;
            pendingStructuralMutation |= structural;
            pendingAmountDeltas.merge(key, amountDelta, Long::sum);
            return;
        }
        commitContentMutation(structural, Map.of(key, amountDelta));
    }

    private void finishMutationBatch() {
        mutationDepth--;
        if (mutationDepth == 0 && pendingMutation) {
            boolean structural = pendingStructuralMutation;
            Map<String, Long> amountDeltas = Map.copyOf(pendingAmountDeltas);
            pendingMutation = false;
            pendingStructuralMutation = false;
            pendingAmountDeltas.clear();
            commitContentMutation(structural, amountDeltas);
        }
    }

    private void commitContentMutation(boolean structural, Map<String, Long> amountDeltas) {
        revision++;
        if (structural) {
            serverOrbitDirty = true;
            orderedKeyCache.clear();
            categoryCache.clear();
            filteredIndexCache.clear();
        } else {
            // Registry, insertion and recent order do not change when only an
            // amount changes. Count order is the sole invalidated ordering.
            orderedKeyCache.remove(EntryOrder.COUNT);
            updateFilteredTotals(amountDeltas);
        }
        setChanged();
        // Counts are not part of the visual pool. Avoid sending 24 item NBT
        // templates for every shift-click; only type changes can alter it.
        if (structural) {
            syncOrbitToClients();
        }
    }

    /**
     * Maintains a small representative reservoir instead of serializing the
     * first entries forever. It is rebuilt only when item types change, so
     * rapid count-only transfers retain the inexpensive update path.
     */
    private List<ItemStack> serverOrbitSnapshot() {
        if (!serverOrbitDirty) {
            return serverOrbitItems;
        }
        serverOrbitItems.clear();
        long randomState = mixOrbitSeed(worldPosition.asLong()
                ^ (long) entries.size() * 0x9E3779B97F4A7C15L
                ^ (long) revision * 0xC2B2AE3D27D4EB4FL);
        int seen = 0;
        for (StoredEntry entry : entries.values()) {
            ItemStack template = entry.template.copyWithCount(1);
            seen++;
            if (serverOrbitItems.size() < ORBIT_ENTRY_LIMIT) {
                serverOrbitItems.add(template);
                continue;
            }
            randomState = mixOrbitSeed(randomState + 0x9E3779B97F4A7C15L);
            int replacement = (int) Math.floorMod(randomState, (long) seen);
            if (replacement < ORBIT_ENTRY_LIMIT) {
                serverOrbitItems.set(replacement, template);
            }
        }
        serverOrbitDirty = false;
        return serverOrbitItems;
    }

    private static long mixOrbitSeed(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private void syncOrbitToClients() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(worldPosition);
        }
    }

    private void updateFilteredTotals(Map<String, Long> amountDeltas) {
        var iterator = filteredIndexCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BrowseCacheKey, FilteredIndex> cached = iterator.next();
            if (cached.getKey().order() == EntryOrder.COUNT) {
                iterator.remove();
                continue;
            }
            long total = cached.getValue().totalItems();
            TerminalFilter.Selection filter = new TerminalFilter.Selection(
                    cached.getKey().query(), cached.getKey().mode(),
                    cached.getKey().category());
            for (Map.Entry<String, Long> delta : amountDeltas.entrySet()) {
                StoredEntry entry = entries.get(delta.getKey());
                if (entry != null && matchesFilter(entry, filter)) {
                    total = saturatedAdd(total, delta.getValue());
                }
            }
            cached.setValue(new FilteredIndex(cached.getValue().keys(), total));
        }
    }

    private static long saturatedAdd(long value, long delta) {
        if (delta > 0 && value > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, value + delta);
    }

    private TerminalFilter.Selection validatedFilter(TerminalFilter.Selection requested) {
        TerminalFilter.Selection safe = requested == null
                ? TerminalFilter.sanitize("", TerminalFilter.CategoryMode.NONE, TerminalFilter.ALL)
                : TerminalFilter.sanitize(requested.query(), requested.mode(), requested.category());
        List<String> categories = availableCategories(safe.mode());
        String category = categories.contains(safe.category()) ? safe.category() : TerminalFilter.ALL;
        return new TerminalFilter.Selection(safe.query(), safe.mode(), category);
    }

    private List<String> availableCategories(TerminalFilter.CategoryMode mode) {
        return categoryCache.computeIfAbsent(mode, requestedMode -> {
            if (requestedMode == TerminalFilter.CategoryMode.NONE) {
                return List.of(TerminalFilter.ALL);
            }
            if (requestedMode == TerminalFilter.CategoryMode.TYPE) {
                Set<String> present = new HashSet<>();
                boolean uncategorized = false;
                for (StoredEntry entry : entries.values()) {
                    List<String> entryCategories = entry.creativeCategories();
                    present.addAll(entryCategories);
                    uncategorized |= entryCategories.isEmpty();
                }
                List<String> categories = new ArrayList<>();
                categories.add(TerminalFilter.ALL);
                for (String category : TerminalFilter.orderedCreativeCategories()) {
                    if (present.contains(category)) {
                        categories.add(category);
                    }
                }
                if (uncategorized) {
                    categories.add(TerminalFilter.UNCATEGORIZED);
                }
                return List.copyOf(categories);
            }
            Set<String> namespaces = new HashSet<>();
            entries.values().forEach(entry -> namespaces.add(entry.modCategory));
            List<String> categories = new ArrayList<>(namespaces);
            categories.sort(String::compareTo);
            categories.add(0, TerminalFilter.ALL);
            return List.copyOf(categories);
        });
    }

    private FilteredIndex buildFilteredIndex(EntryOrder order, TerminalFilter.Selection filter) {
        List<String> ordered = orderedKeyCache.computeIfAbsent(order, this::orderedKeys);
        if (filter.query().isBlank() && TerminalFilter.ALL.equals(filter.category())) {
            return new FilteredIndex(ordered, totalItems);
        }
        List<String> filtered = new ArrayList<>();
        long filteredItems = 0;
        for (String key : ordered) {
            StoredEntry entry = entries.get(key);
            if (entry == null || !matchesFilter(entry, filter)) {
                continue;
            }
            filtered.add(key);
            filteredItems = Long.MAX_VALUE - filteredItems < entry.count
                    ? Long.MAX_VALUE : filteredItems + entry.count;
        }
        return new FilteredIndex(List.copyOf(filtered), filteredItems);
    }

    private boolean matchesFilter(StoredEntry entry, TerminalFilter.Selection filter) {
        if (!filter.query().isBlank()
                && !TerminalFilter.matchesQuery(entry.searchText(), filter.query())) {
            return false;
        }
        return switch (filter.mode()) {
            case NONE -> true;
            case TYPE -> TerminalFilter.ALL.equals(filter.category())
                    || (TerminalFilter.UNCATEGORIZED.equals(filter.category())
                    ? entry.creativeCategories().isEmpty()
                    : entry.creativeCategories().contains(filter.category()));
            case MOD -> TerminalFilter.ALL.equals(filter.category())
                    || entry.modCategory.equals(filter.category());
        };
    }

    private List<String> orderedKeys(EntryOrder order) {
        List<String> keys = new ArrayList<>(entries.keySet());
        switch (order) {
            case REGISTRY -> keys.sort(Comparator
                    .comparing((String key) -> BuiltInRegistries.ITEM.getKey(
                            entries.get(key).template.getItem()).toString())
                    .thenComparing(key -> key));
            case COUNT -> keys.sort(Comparator
                    .comparingLong((String key) -> entries.get(key).count).reversed()
                    .thenComparing(key -> key));
            case RECENT -> java.util.Collections.reverse(keys);
            case INSERTION -> {
                // LinkedHashMap order already matches the legacy page method.
            }
        }
        return List.copyOf(keys);
    }

    private static final class StoredEntry {
        private final ItemStack template;
        private final String modCategory;
        private List<String> creativeCategories;
        private int creativeGeneration = -1;
        private String searchText;
        private long count;

        private StoredEntry(ItemStack template, long count) {
            this.template = template;
            this.modCategory = TerminalFilter.modCategory(template);
            this.count = count;
        }

        private List<String> creativeCategories() {
            if (creativeCategories == null
                    || creativeGeneration != TerminalFilter.creativeGeneration()) {
                creativeCategories = TerminalFilter.creativeCategories(template);
                creativeGeneration = TerminalFilter.creativeGeneration();
            }
            return creativeCategories;
        }

        private String searchText() {
            if (searchText == null) {
                searchText = TerminalFilter.searchText(template);
            }
            return searchText;
        }
    }

    private void ensureCreativeCategoriesCurrent() {
        int generation = TerminalFilter.creativeGeneration();
        if (seenCreativeGeneration == generation) {
            return;
        }
        seenCreativeGeneration = generation;
        categoryCache.remove(TerminalFilter.CategoryMode.TYPE);
        filteredIndexCache.clear();
    }

    private record BrowseCacheKey(EntryOrder order, String query,
                                  TerminalFilter.CategoryMode mode, String category) {
    }

    private record FilteredIndex(List<String> keys, long totalItems) {
    }

    public record EntryView(String key, ItemStack stack, long count) {
    }

    public record BrowseResult(List<EntryView> entries, int page, int pageCount,
                               int filteredTypes, long filteredItems,
                               List<String> categories, String selectedCategory) {
    }

    public enum EntryOrder {
        REGISTRY,
        COUNT,
        RECENT,
        INSERTION
    }
}
