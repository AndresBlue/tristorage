package com.andresblue.tristorage.blockentity;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.StorageCoreBlock;
import com.andresblue.tristorage.item.StorageCoreBlockItem;
import com.andresblue.tristorage.screen.CoreScreenHandler;
import com.andresblue.tristorage.storage.CoreStorageData;
import com.andresblue.tristorage.storage.StorageCategory;
import com.andresblue.tristorage.storage.StorageTier;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Persistent core storage with the same cached browse pipeline used by 1.9.
 * Amount-only mutations update cached totals in place; structural mutations
 * invalidate only the indices that can actually have changed.
 */
public final class StorageCoreBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {
    private static final String CHESTS_KEY = "InstalledChests";
    private static final String ENTRIES_KEY = "Entries";
    private static final int ORBIT_ENTRY_LIMIT = 24;
    private static final int FILTER_CACHE_LIMIT = 16;

    private final Map<String, StoredEntry> entries = new LinkedHashMap<>();
    private final Map<EntryOrder, List<String>> orderedKeyCache = new EnumMap<>(EntryOrder.class);
    private final Map<TerminalFilter.CategoryMode, List<StorageCategory>> categoryCache =
            new EnumMap<>(TerminalFilter.CategoryMode.class);
    private final Map<BrowseCacheKey, FilteredIndex> filteredIndexCache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<BrowseCacheKey, FilteredIndex> eldest) {
                    return size() > FILTER_CACHE_LIMIT;
                }
            };

    private final Map<String, Long> pendingAmountDeltas = new HashMap<>();
    private final List<ItemStack> orbitItems = new ArrayList<>();
    private int installedChests;
    private long totalItems;
    private int revision;
    private int orbitRevision;
    private int seenCreativeGeneration = -1;
    private int mutationDepth;
    private boolean pendingMutation;
    private boolean pendingStructuralMutation;
    private boolean orbitDirty = true;

    public StorageCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TriStorageMod.STORAGE_CORE_BLOCK_ENTITY, pos, state);
    }

    public StorageTier tier() {
        return getCachedState().getBlock() instanceof StorageCoreBlock core
                ? core.tier() : StorageTier.IRON;
    }

    public int installedChests() { return installedChests; }
    public int maxChests() { return tier().chestCapacity(); }
    public int typeCapacity() { return tier().typeCapacity(installedChests); }
    public long itemCapacity() { return tier().itemCapacity(installedChests); }
    public int storedTypes() { return entries.size(); }
    public long totalItems() { return totalItems; }
    public int revision() { return revision; }

    public int addChests(int requested) {
        int accepted = Math.min(Math.max(0, requested), maxChests() - installedChests);
        if (accepted > 0) {
            installedChests += accepted;
            metadataChanged();
        }
        return accepted;
    }

    public int removableChests(int requested) {
        for (int removable = Math.min(Math.max(0, requested), installedChests);
             removable > 0; removable--) {
            int remaining = installedChests - removable;
            if (entries.size() <= tier().typeCapacity(remaining)
                    && totalItems <= tier().itemCapacity(remaining)) {
                return removable;
            }
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
                || requested <= 0L || installedChests <= 0) {
            return 0L;
        }
        String key = matchingKey(source);
        StoredEntry existing = key == null ? null : entries.get(key);
        if (existing == null && entries.size() >= typeCapacity()) {
            return 0L;
        }
        long accepted = Math.min(requested, itemCapacity() - totalItems);
        if (accepted <= 0L) {
            return 0L;
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
        int extracted = (int) Math.min(Math.min(existing.count, (long) requested),
                existing.template.getMaxCount());
        ItemStack result = existing.template.copyWithCount(extracted);
        existing.count -= extracted;
        totalItems -= extracted;
        boolean structural = existing.count <= 0L;
        if (structural) {
            entries.remove(key);
        }
        contentChanged(structural, key, -extracted);
        return result;
    }

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

    public List<EntryView> page(int requestedPage, int pageSize, EntryOrder order) {
        int safePage = Math.max(0, requestedPage);
        int start = safePage * pageSize;
        if (start >= entries.size()) {
            return List.of();
        }
        List<String> ordered = orderedKeyCache.computeIfAbsent(order, this::orderedKeys);
        int end = Math.min(start + pageSize, ordered.size());
        List<EntryView> result = new ArrayList<>(end - start);
        for (int index = start; index < end; index++) {
            String key = ordered.get(index);
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
        FilteredIndex index = filteredIndexCache.computeIfAbsent(cacheKey,
                ignored -> buildFilteredIndex(order, filter));
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
        return new BrowseResult(visible, page, pageCount, index.keys().size(),
                index.totalItems(), availableCategories(filter.mode()), filter.category());
    }

    public Collection<EntryView> allEntries() {
        List<EntryView> result = new ArrayList<>(entries.size());
        entries.forEach((key, value) ->
                result.add(new EntryView(key, value.template.copy(), value.count)));
        return result;
    }

    public List<ItemStack> orbitItemSnapshot() {
        if (orbitDirty) {
            rebuildOrbitSnapshot();
        }
        return orbitItems.stream().map(ItemStack::copy).toList();
    }

    public int orbitSnapshotRevision() {
        return orbitRevision;
    }

    public CoreStorageData snapshot() {
        List<CoreStorageData.StoredStack> stored = new ArrayList<>(entries.size());
        entries.values().forEach(entry -> stored.add(
                new CoreStorageData.StoredStack(entry.template, entry.count)));
        return new CoreStorageData(installedChests, stored);
    }

    public void loadSnapshot(CoreStorageData data) {
        installedChests = Math.min(data.installedChests(), maxChests());
        entries.clear();
        totalItems = 0L;
        long capacity = itemCapacity();
        int typeLimit = typeCapacity();
        for (CoreStorageData.StoredStack stored : data.entries()) {
            if (stored.stack().isEmpty() || stored.count() <= 0L
                    || entries.size() >= typeLimit) {
                continue;
            }
            long accepted = Math.min(stored.count(), capacity - totalItems);
            if (accepted <= 0L) {
                break;
            }
            ItemStack template = stored.stack().copyWithCount(1);
            String key = matchingKey(template);
            StoredEntry existing = key == null ? null : entries.get(key);
            if (existing == null) {
                entries.put(uniqueKey(template), new StoredEntry(template, accepted));
            } else {
                existing.count += accepted;
            }
            totalItems += accepted;
        }
        invalidateAllCaches();
        revision++;
        orbitRevision++;
        orbitDirty = true;
        markDirty();
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("screen.tristorage.core", tier().level());
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory,
                                    PlayerEntity player) {
        return new CoreScreenHandler(syncId, playerInventory, this);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.putInt(CHESTS_KEY, installedChests);
        WriteView.ListView list = view.getList(ENTRIES_KEY);
        for (StoredEntry entry : entries.values()) {
            WriteView stored = list.add();
            stored.put("Stack", ItemStack.CODEC, entry.template);
            stored.putLong("Count", entry.count);
        }
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        installedChests = Math.min(Math.max(0, view.getInt(CHESTS_KEY, 0)), maxChests());
        entries.clear();
        totalItems = 0L;
        for (ReadView stored : view.getListReadView(ENTRIES_KEY)) {
            ItemStack stack = stored.read("Stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            long count = Math.max(0L, stored.getLong("Count", 0L));
            if (stack.isEmpty() || count <= 0L) {
                continue;
            }
            ItemStack template = stack.copyWithCount(1);
            String key = matchingKey(template);
            StoredEntry existing = key == null ? null : entries.get(key);
            if (existing == null) {
                entries.put(uniqueKey(template), new StoredEntry(template, count));
            } else {
                existing.count = saturatedAdd(existing.count, count);
            }
            totalItems = saturatedAdd(totalItems, count);
        }
        invalidateAllCaches();
        revision++;
        orbitRevision++;
        orbitDirty = true;
    }

    @Override
    protected void addComponents(ComponentMap.Builder builder) {
        super.addComponents(builder);
        if (installedChests > 0 || totalItems > 0L) {
            builder.add(TriStorageMod.CORE_STORAGE, snapshot());
        }
    }

    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        CoreStorageData portable = components.get(TriStorageMod.CORE_STORAGE);
        if (portable != null) {
            loadSnapshot(portable);
        }
    }

    @Override
    public void removeFromCopiedStackData(WriteView view) {
        super.removeFromCopiedStackData(view);
        view.remove(CHESTS_KEY);
        view.remove(ENTRIES_KEY);
    }

    public static String keyOf(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id + "|" + stack.getComponentChanges();
    }

    @Nullable
    private String matchingKey(ItemStack stack) {
        String directKey = keyOf(stack);
        StoredEntry direct = entries.get(directKey);
        if (direct != null && ItemStack.areItemsAndComponentsEqual(direct.template, stack)) {
            return directKey;
        }
        return null;
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
        markDirty();
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
            Map<String, Long> deltas = Map.copyOf(pendingAmountDeltas);
            pendingMutation = false;
            pendingStructuralMutation = false;
            pendingAmountDeltas.clear();
            commitContentMutation(structural, deltas);
        }
    }

    private void commitContentMutation(boolean structural, Map<String, Long> amountDeltas) {
        revision++;
        if (structural) {
            orderedKeyCache.clear();
            categoryCache.clear();
            filteredIndexCache.clear();
            orbitDirty = true;
            orbitRevision++;
        } else {
            orderedKeyCache.remove(EntryOrder.COUNT);
            updateFilteredTotals(amountDeltas);
        }
        markDirty();
    }

    private void updateFilteredTotals(Map<String, Long> amountDeltas) {
        Iterator<Map.Entry<BrowseCacheKey, FilteredIndex>> iterator =
                filteredIndexCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BrowseCacheKey, FilteredIndex> cached = iterator.next();
            if (cached.getKey().order() == EntryOrder.COUNT) {
                iterator.remove();
                continue;
            }
            long total = cached.getValue().totalItems();
            TerminalFilter.Selection filter = new TerminalFilter.Selection(
                    cached.getKey().query(), cached.getKey().mode(), cached.getKey().category());
            for (Map.Entry<String, Long> delta : amountDeltas.entrySet()) {
                StoredEntry entry = entries.get(delta.getKey());
                if (entry != null && matchesFilter(entry, filter)) {
                    total = saturatedAdd(total, delta.getValue());
                }
            }
            cached.setValue(new FilteredIndex(cached.getValue().keys(), total));
        }
    }

    private TerminalFilter.Selection validatedFilter(TerminalFilter.Selection requested) {
        TerminalFilter.Selection safe = requested == null
                ? TerminalFilter.sanitize("", TerminalFilter.CategoryMode.NONE, TerminalFilter.ALL)
                : TerminalFilter.sanitize(requested.query(), requested.mode(), requested.category());
        List<StorageCategory> categories = availableCategories(safe.mode());
        String selected = categories.stream().map(StorageCategory::id)
                .anyMatch(safe.category()::equals) ? safe.category() : TerminalFilter.ALL;
        return new TerminalFilter.Selection(safe.query(), safe.mode(), selected);
    }

    private List<StorageCategory> availableCategories(TerminalFilter.CategoryMode mode) {
        return categoryCache.computeIfAbsent(mode,
                requested -> StorageCategory.build(allEntries(), requested));
    }

    private FilteredIndex buildFilteredIndex(EntryOrder order,
                                             TerminalFilter.Selection filter) {
        List<String> ordered = orderedKeyCache.computeIfAbsent(order, this::orderedKeys);
        if (filter.query().isBlank() && TerminalFilter.ALL.equals(filter.category())) {
            return new FilteredIndex(ordered, totalItems);
        }
        List<String> filtered = new ArrayList<>();
        long filteredItems = 0L;
        for (String key : ordered) {
            StoredEntry entry = entries.get(key);
            if (entry != null && matchesFilter(entry, filter)) {
                filtered.add(key);
                filteredItems = saturatedAdd(filteredItems, entry.count);
            }
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
                    .<String, String>comparing(key -> Registries.ITEM
                            .getId(entries.get(key).template.getItem()).toString())
                    .thenComparing(key -> key));
            case COUNT -> keys.sort(Comparator
                    .<String>comparingLong(key -> entries.get(key).count).reversed()
                    .thenComparing(key -> key));
            case RECENT -> Collections.reverse(keys);
            case INSERTION -> { }
        }
        return List.copyOf(keys);
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

    private void invalidateAllCaches() {
        orderedKeyCache.clear();
        categoryCache.clear();
        filteredIndexCache.clear();
    }

    private void rebuildOrbitSnapshot() {
        orbitItems.clear();
        long randomState = mixOrbitSeed(pos.asLong()
                ^ entries.size() * -7046029254386353131L
                ^ orbitRevision * -4417276706812531889L);
        int seen = 0;
        for (StoredEntry entry : entries.values()) {
            ItemStack template = entry.template.copyWithCount(1);
            seen++;
            if (orbitItems.size() < ORBIT_ENTRY_LIMIT) {
                orbitItems.add(template);
            } else {
                randomState = mixOrbitSeed(randomState + -7046029254386353131L);
                int replacement = (int) Math.floorMod(randomState, (long) seen);
                if (replacement < ORBIT_ENTRY_LIMIT) {
                    orbitItems.set(replacement, template);
                }
            }
        }
        orbitDirty = false;
    }

    private static long mixOrbitSeed(long value) {
        long mixed = (value ^ value >>> 30) * -4658895280553007687L;
        mixed = (mixed ^ mixed >>> 27) * -7723592293110705685L;
        return mixed ^ mixed >>> 31;
    }

    private static long saturatedAdd(long value, long delta) {
        if (delta > 0L && value > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, value + delta);
    }

    public enum EntryOrder {
        REGISTRY,
        COUNT,
        RECENT,
        INSERTION
    }

    public record EntryView(String key, ItemStack stack, long count) {
    }

    public record BrowseResult(List<EntryView> entries, int page, int pageCount,
                               int filteredTypes, long filteredItems,
                               List<StorageCategory> categories,
                               String selectedCategory) {
    }

    private record BrowseCacheKey(EntryOrder order, String query,
                                  TerminalFilter.CategoryMode mode, String category) {
    }

    private record FilteredIndex(List<String> keys, long totalItems) {
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
}
