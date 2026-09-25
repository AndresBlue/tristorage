package com.andresblue.tristorage.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.Iterator;
import java.util.Set;

/**
 * One authoritative, incrementally indexed storage. World and BlockEntity
 * state never enter this class, making it suitable for repository ownership.
 */
public final class StorageRuntime {
    private static final int MAX_CACHED_VIEWS = 32;
    private StorageId id;
    private final Map<ItemKey, Entry> byKey = new HashMap<>();
    private final Map<Long, Entry> byId = new HashMap<>();
    private final Map<Item, LinkedHashSet<Entry>> byItem = new IdentityHashMap<>();
    private final NavigableSet<Entry> byRegistry = new TreeSet<>(registryComparator());
    private final NavigableSet<Entry> byCount = new TreeSet<>(countComparator());
    private final NavigableSet<Entry> byInsertion = new TreeSet<>(Comparator.comparingLong(Entry::id));
    private final Map<String, Integer> modCategoryCounts = new HashMap<>();
    private final Map<String, Integer> creativeCategoryCounts = new HashMap<>();
    private final Map<String, LinkedHashSet<Entry>> modCategoryEntries = new HashMap<>();
    private final Map<String, LinkedHashSet<Entry>> creativeCategoryEntries = new HashMap<>();
    private final Map<EntryOrder, PageCursorCache> globalPageCursors =
            new EnumMap<>(EntryOrder.class);
    private final Map<ViewKey, ViewState> views = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<PageKey, PageSlice> pageSnapshots =
            new LinkedHashMap<>(32, 0.75f, true);
    private final Map<Long, Long> pendingDeltas = new HashMap<>();
    private final Set<Long> pendingAddedIds = new HashSet<>();
    private final Set<Long> pendingRemovedIds = new HashSet<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int installedChests;
    private long totalItems;
    private long nextEntryId = 1;
    private long contentRevision;
    private long structureRevision;
    private long categoryRevision;
    private boolean pendingMutation;
    private boolean pendingCategories;
    private int creativeGeneration;
    private boolean ready = true;
    private long stateEpoch;

    public StorageRuntime(StorageId id) {
        this.id = id;
        this.creativeGeneration = TerminalFilter.creativeGeneration();
    }

    public StorageId id() {
        return id;
    }

    /** Assigns the deterministic legacy id before this runtime is published. */
    public void assignIdBeforeReady(StorageId replacement) {
        if (ready) {
            throw new IllegalStateException("A published storage id is immutable");
        }
        id = replacement;
    }

    public boolean isReady() {
        return ready;
    }

    public void beginLoading() {
        ready = false;
        if (byId.isEmpty()) {
            creativeGeneration = TerminalFilter.creativeGeneration();
            creativeCategoryCounts.clear();
            creativeCategoryEntries.clear();
        }
    }

    public void setListener(Listener listener) {
        listeners.clear();
        if (listener != null && listener != Listener.NONE) {
            listeners.add(listener);
        }
    }

    public void addListener(Listener listener) {
        if (listener != null && listener != Listener.NONE && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public int installedChests() {
        return installedChests;
    }

    public void setInstalledChests(int value) {
        int safe = Math.max(0, value);
        if (installedChests != safe) {
            installedChests = safe;
            noteMutation(false, false, 0, 0);
        }
    }

    public void loadInstalledChests(int value) {
        installedChests = Math.max(0, value);
    }

    public int storedTypes() {
        return byId.size();
    }

    public long totalItems() {
        return totalItems;
    }

    public long revision() {
        return contentRevision;
    }

    public long structureRevision() {
        return structureRevision;
    }

    public long categoryRevision() {
        return categoryRevision;
    }

    /** Changes immediately for every authoritative mutation, before tick batching. */
    public long stateEpoch() {
        return stateEpoch;
    }

    public long estimatedBytes() {
        long estimate = 4_096L + (long) byId.size() * 408L
                + (long) byItem.size() * 64L;
        for (LinkedHashSet<Entry> entries : modCategoryEntries.values()) {
            estimate = saturatedAdd(estimate, 96L + (long) entries.size() * 16L);
        }
        for (LinkedHashSet<Entry> entries : creativeCategoryEntries.values()) {
            estimate = saturatedAdd(estimate, 96L + (long) entries.size() * 16L);
        }
        for (ViewState view : views.values()) {
            estimate = saturatedAdd(estimate, (long) view.entries.size() * 24L + 128L);
        }
        return estimate;
    }

    public long insert(ItemStack source, long requested, int typeCapacity, long itemCapacity) {
        if (!ready || source.isEmpty() || requested <= 0) {
            return 0;
        }
        long keyStarted = StorageMetrics.startTimer();
        Entry entry = byKey.get(ItemKey.probe(source));
        StorageMetrics.stopTimer("item_key_lookup", keyStarted);
        if (entry == null && byId.size() >= typeCapacity) {
            return 0;
        }
        long accepted = Math.min(requested, itemCapacity - totalItems);
        if (accepted <= 0) {
            return 0;
        }
        if (entry == null) {
            ItemKey key = ItemKey.frozen(source);
            ItemStack template = key.toStack();
            template.setCount(1);
            entry = new Entry(nextEntryId++, key, template, accepted);
            addEntry(entry);
            noteMutation(true, true, entry.id, accepted);
        } else {
            byCount.remove(entry);
            removeFromCountViews(entry);
            entry.count += accepted;
            byCount.add(entry);
            restoreCountViewsAndApplyDelta(entry, accepted);
            noteMutation(false, false, entry.id, accepted);
        }
        totalItems += accepted;
        StorageMetrics.increment("mutations.insert");
        return accepted;
    }

    public ItemStack extract(long entryId, int requested) {
        if (!ready) {
            return ItemStack.EMPTY;
        }
        Entry entry = byId.get(entryId);
        if (entry == null || requested <= 0) {
            return ItemStack.EMPTY;
        }
        int extracted = (int) Math.min(Math.min(entry.count, requested), entry.template.getMaxStackSize());
        ItemStack result = entry.template.copy();
        StorageMetrics.increment("item_stack_copies");
        result.setCount(extracted);
        byCount.remove(entry);
        removeFromCountViews(entry);
        applyViewTotalDelta(entry, -extracted);
        entry.count -= extracted;
        totalItems -= extracted;
        if (entry.count <= 0) {
            removeEntry(entry);
            noteMutation(true, true, entry.id, -extracted);
        } else {
            byCount.add(entry);
            restoreCountViews(entry);
            noteMutation(false, false, entry.id, -extracted);
        }
        StorageMetrics.increment("mutations.extract");
        return result;
    }

    /** Extracts the exact ItemStack variant, including NBT, in expected O(1). */
    public ItemStack extractMatching(ItemStack template, int requested) {
        if (!ready || template == null || template.isEmpty() || requested <= 0) {
            return ItemStack.EMPTY;
        }
        Entry entry = byKey.get(ItemKey.probe(template));
        return entry == null ? ItemStack.EMPTY : extract(entry.id, requested);
    }

    public BrowseResult browse(int requestedPage, int pageSize, EntryOrder order,
                               TerminalFilter.Selection requestedFilter) {
        if (!ready) {
            return new BrowseResult(List.of(), 0, 1, 0, 0,
                    List.of(TerminalFilter.ALL), TerminalFilter.ALL, contentRevision);
        }
        long started = StorageMetrics.startTimer();
        ensureCreativeCategoriesCurrent();
        TerminalFilter.Selection filter = validateFilter(requestedFilter);
        NavigableSet<Entry> source;
        PageCursorCache cursors;
        long filteredItems;
        boolean unfiltered = filter.query().isBlank()
                && TerminalFilter.ALL.equals(filter.category());
        if (unfiltered) {
            source = ordered(order);
            cursors = globalPageCursors.computeIfAbsent(order,
                    ignored -> new PageCursorCache());
            filteredItems = totalItems;
        } else {
            ViewKey key = viewKey(order, filter);
            ViewState view = views.computeIfAbsent(key, this::buildView);
            source = view.entries;
            cursors = view.pageCursors;
            filteredItems = view.totalItems;
        }
        int filteredTypes = source.size();
        int pageCount = Math.max(1, (filteredTypes + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        PageKey pageKey = new PageKey(order, filter.query(), filter.mode(),
                filter.category(), page, pageSize, stateEpoch);
        PageSlice slice = pageSnapshots.get(pageKey);
        if (slice == null) {
            slice = paginate(source, cursors, page, pageSize);
            pageSnapshots.put(pageKey, slice);
            trimPageSnapshots();
        } else {
            StorageMetrics.increment("page_snapshot_hits");
        }
        List<EntryView> visible = slice.entries;
        StorageMetrics.add("page.entries_visited", slice.visited);
        StorageMetrics.stopTimer("page_build", started);
        return new BrowseResult(visible, page, pageCount, filteredTypes, filteredItems,
                availableCategories(filter.mode()), filter.category(), contentRevision);
    }

    /** Pins a filtered view while a handler is using it. */
    public ViewLease retainView(EntryOrder order, TerminalFilter.Selection requestedFilter) {
        if (!ready) {
            return ViewLease.EMPTY;
        }
        ensureCreativeCategoriesCurrent();
        TerminalFilter.Selection filter = validateFilter(requestedFilter);
        if (filter.query().isBlank() && TerminalFilter.ALL.equals(filter.category())) {
            return ViewLease.EMPTY;
        }
        ViewKey key = viewKey(order, filter);
        ViewState state = views.computeIfAbsent(key, this::buildView);
        state.references++;
        trimViews();
        return new ViewLease(this, key);
    }

    private void releaseView(ViewKey key) {
        ViewState state = views.get(key);
        if (state != null && state.references > 0) {
            state.references--;
        }
        trimViews();
    }

    private void trimViews() {
        if (views.size() <= MAX_CACHED_VIEWS) {
            return;
        }
        Iterator<Map.Entry<ViewKey, ViewState>> iterator = views.entrySet().iterator();
        while (views.size() > MAX_CACHED_VIEWS && iterator.hasNext()) {
            if (iterator.next().getValue().references == 0) {
                iterator.remove();
                StorageMetrics.increment("view_evictions");
            }
        }
    }

    private void trimPageSnapshots() {
        Iterator<PageKey> iterator = pageSnapshots.keySet().iterator();
        while (pageSnapshots.size() > 128 && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    public List<SnapshotEntry> snapshotEntries() {
        List<SnapshotEntry> result = new ArrayList<>(byInsertion.size());
        for (Entry entry : byInsertion) {
            result.add(new SnapshotEntry(entry.id, entry.template.copy(), entry.count));
        }
        return result;
    }

    public SnapshotCursor snapshotCursor() {
        return new SnapshotCursor(byInsertion.iterator());
    }

    public List<ItemStack> sampleTemplates(int limit) {
        if (limit <= 0 || byInsertion.isEmpty()) {
            return List.of();
        }
        List<ItemStack> result = new ArrayList<>(Math.min(limit, byInsertion.size()));
        for (Entry entry : byInsertion) {
            ItemStack stack = entry.template.copy();
            stack.setCount(1);
            result.add(stack);
            if (result.size() == limit) {
                break;
            }
        }
        return result;
    }

    public SnapshotEntry snapshotEntry(long entryId) {
        Entry entry = byId.get(entryId);
        return entry == null ? null
                : new SnapshotEntry(entry.id, entry.template.copy(), entry.count);
    }

    /**
     * Returns all exact stored variants accepted by an ingredient without
     * depending on the terminal's current page, filter, or sort order.
     * Candidate registry items are resolved first, so ordinary recipe fills
     * touch only the variants of those items instead of scanning the catalog.
     */
    public List<SnapshotEntry> matchingEntries(Ingredient ingredient) {
        return matchingEntries(ingredient, 9);
    }

    public List<SnapshotEntry> matchingEntries(Ingredient ingredient, int limit) {
        if (!ready || ingredient == null || ingredient.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, limit);
        Set<Item> candidates = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack matching : ingredient.getItems()) {
            if (!matching.isEmpty()) {
                candidates.add(matching.getItem());
            }
        }
        Collection<Entry> source;
        if (candidates.isEmpty()) {
            // Compatibility fallback for custom ingredients that implement
            // test() dynamically without advertising matching stacks.
            source = byInsertion;
            StorageMetrics.increment("recipe_transfer_fallback_scans");
        } else {
            List<Entry> indexed = new ArrayList<>();
            for (Item item : candidates) {
                Collection<Entry> variants = byItem.get(item);
                if (variants != null) {
                    indexed.addAll(variants);
                }
            }
            source = indexed;
        }
        // A 3x3 grid can consume at most nine exact variants. Keeping the
        // highest-count accepted candidates avoids copying enormous catalogs
        // made from NBT variants while retaining every feasible assignment.
        List<Entry> best = new ArrayList<>(Math.min(safeLimit, 9));
        for (Entry entry : source) {
            if (ingredient.test(entry.template)) {
                retainCandidate(best, entry, safeLimit);
            }
        }
        List<SnapshotEntry> result = new ArrayList<>(best.size());
        for (Entry entry : best) {
            result.add(new SnapshotEntry(entry.id, entry.template.copy(), entry.count));
        }
        StorageMetrics.add("recipe_transfer_candidates", result.size());
        return result;
    }

    /**
     * Defensive one-pass fallback used when an indexed recipe plan cannot be
     * built. Besides supporting unusual dynamic ingredients, this repairs
     * compatibility with runtimes created before the per-item index existed.
     */
    public List<SnapshotEntry> matchingAnyEntries(List<Ingredient> ingredients,
                                                  int perIngredientLimit) {
        if (!ready || ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, perIngredientLimit);
        List<List<Entry>> selected = new ArrayList<>(ingredients.size());
        for (int index = 0; index < ingredients.size(); index++) {
            selected.add(new ArrayList<>(Math.min(limit, 9)));
        }
        for (Entry entry : byInsertion) {
            for (int index = 0; index < ingredients.size(); index++) {
                Ingredient ingredient = ingredients.get(index);
                if (!ingredient.isEmpty() && ingredient.test(entry.template)) {
                    retainCandidate(selected.get(index), entry, limit);
                }
            }
        }
        Map<Long, Entry> unique = new LinkedHashMap<>();
        for (List<Entry> matches : selected) {
            for (Entry entry : matches) {
                unique.putIfAbsent(entry.id, entry);
                // Also heal the incremental index if this runtime predates it.
                byItem.computeIfAbsent(entry.template.getItem(),
                        ignored -> new LinkedHashSet<>()).add(entry);
            }
        }
        List<SnapshotEntry> result = new ArrayList<>(unique.size());
        for (Entry entry : unique.values()) {
            result.add(new SnapshotEntry(entry.id, entry.template.copy(), entry.count));
        }
        StorageMetrics.increment("recipe_transfer_full_fallbacks");
        StorageMetrics.add("recipe_transfer_fallback_candidates", result.size());
        return result;
    }

    private static void retainCandidate(List<Entry> best, Entry entry, int limit) {
        int insertion = 0;
        while (insertion < best.size() && best.get(insertion).count >= entry.count) {
            insertion++;
        }
        if (insertion < limit) {
            best.add(insertion, entry);
            if (best.size() > limit) {
                best.remove(best.size() - 1);
            }
        }
    }

    public void loadEntry(long entryId, ItemStack template, long count) {
        if (template.isEmpty() || count <= 0) {
            return;
        }
        ItemKey probe = ItemKey.probe(template);
        Entry existing = byKey.get(probe);
        if (existing != null) {
            byCount.remove(existing);
            existing.count = saturatedAdd(existing.count, count);
            byCount.add(existing);
            totalItems = saturatedAdd(totalItems, count);
            nextEntryId = Math.max(nextEntryId, Math.max(1, entryId) + 1);
            return;
        }
        ItemStack one = template.copy();
        one.setCount(1);
        Entry entry = new Entry(Math.max(1, entryId), ItemKey.frozen(one), one, count);
        addEntry(entry);
        totalItems = saturatedAdd(totalItems, count);
        nextEntryId = Math.max(nextEntryId, entry.id + 1);
    }

    public void finishLoading(long revision) {
        boolean notifyHandlers = !ready;
        contentRevision = Math.max(contentRevision, revision);
        if (notifyHandlers) {
            contentRevision++;
        }
        structureRevision = contentRevision;
        categoryRevision = contentRevision;
        pendingMutation = false;
        pendingDeltas.clear();
        pendingAddedIds.clear();
        pendingRemovedIds.clear();
        ready = true;
        stateEpoch++;
    }

    void flushChanges() {
        if (!pendingMutation) {
            return;
        }
        contentRevision++;
        boolean structural = !pendingAddedIds.isEmpty() || !pendingRemovedIds.isEmpty();
        if (structural) {
            structureRevision++;
        }
        if (structural && pendingCategories) {
            categoryRevision++;
        }
        ChangeSet changes = new ChangeSet(contentRevision, structureRevision,
                categoryRevision, Map.copyOf(pendingDeltas), structural);
        pendingMutation = false;
        pendingCategories = false;
        pendingDeltas.clear();
        pendingAddedIds.clear();
        pendingRemovedIds.clear();
        StorageMetrics.increment("mutation_batches");
        StorageMetrics.increment("revisions");
        for (Listener listener : List.copyOf(listeners)) {
            listener.onFlush(this, changes);
        }
    }

    private void noteMutation(boolean structural, boolean categories, long id, long delta) {
        stateEpoch++;
        pendingMutation = true;
        pendingCategories |= categories;
        if (structural && id > 0) {
            if (delta > 0) {
                if (!pendingRemovedIds.remove(id)) {
                    pendingAddedIds.add(id);
                }
            } else if (!pendingAddedIds.remove(id)) {
                pendingRemovedIds.add(id);
            }
        }
        if (id > 0 && delta != 0) {
            pendingDeltas.merge(id, delta, Long::sum);
            if (pendingDeltas.get(id) == 0) {
                pendingDeltas.remove(id);
            }
        }
        StorageTickCoordinator.markDirty(this);
    }

    private void addEntry(Entry entry) {
        byKey.put(entry.key, entry);
        byId.put(entry.id, entry);
        byItem.computeIfAbsent(entry.template.getItem(), ignored -> new LinkedHashSet<>())
                .add(entry);
        byRegistry.add(entry);
        byCount.add(entry);
        byInsertion.add(entry);
        increment(modCategoryCounts, entry.modCategory);
        addCategoryEntry(modCategoryEntries, entry.modCategory, entry);
        List<String> categories = entry.creativeCategories();
        if (categories.isEmpty()) {
            increment(creativeCategoryCounts, TerminalFilter.UNCATEGORIZED);
            addCategoryEntry(creativeCategoryEntries, TerminalFilter.UNCATEGORIZED, entry);
        } else {
            categories.forEach(category -> {
                increment(creativeCategoryCounts, category);
                addCategoryEntry(creativeCategoryEntries, category, entry);
            });
        }
        for (Map.Entry<ViewKey, ViewState> cached : views.entrySet()) {
            if (matches(entry, cached.getKey())) {
                cached.getValue().entries.add(entry);
                cached.getValue().totalItems = saturatedAdd(cached.getValue().totalItems, entry.count);
                cached.getValue().pageCursors.clear();
            }
        }
        globalPageCursors.values().forEach(PageCursorCache::clear);
    }

    private void removeEntry(Entry entry) {
        byKey.remove(entry.key);
        byId.remove(entry.id);
        LinkedHashSet<Entry> variants = byItem.get(entry.template.getItem());
        if (variants != null) {
            variants.remove(entry);
            if (variants.isEmpty()) {
                byItem.remove(entry.template.getItem());
            }
        }
        byRegistry.remove(entry);
        byInsertion.remove(entry);
        decrement(modCategoryCounts, entry.modCategory);
        removeCategoryEntry(modCategoryEntries, entry.modCategory, entry);
        List<String> categories = entry.creativeCategories();
        if (categories.isEmpty()) {
            decrement(creativeCategoryCounts, TerminalFilter.UNCATEGORIZED);
            removeCategoryEntry(creativeCategoryEntries, TerminalFilter.UNCATEGORIZED, entry);
        } else {
            categories.forEach(category -> {
                decrement(creativeCategoryCounts, category);
                removeCategoryEntry(creativeCategoryEntries, category, entry);
            });
        }
        for (ViewState view : views.values()) {
            if (view.entries.remove(entry)) {
                view.totalItems = Math.max(0, view.totalItems - entry.count);
                view.pageCursors.clear();
            }
        }
        globalPageCursors.values().forEach(PageCursorCache::clear);
    }

    private void removeFromCountViews(Entry entry) {
        PageCursorCache global = globalPageCursors.get(EntryOrder.COUNT);
        if (global != null) {
            global.clear();
        }
        for (Map.Entry<ViewKey, ViewState> cached : views.entrySet()) {
            if (cached.getKey().order == EntryOrder.COUNT) {
                cached.getValue().entries.remove(entry);
                cached.getValue().pageCursors.clear();
            }
        }
    }

    private void restoreCountViewsAndApplyDelta(Entry entry, long delta) {
        applyViewTotalDelta(entry, delta);
        restoreCountViews(entry);
    }

    private void restoreCountViews(Entry entry) {
        for (Map.Entry<ViewKey, ViewState> cached : views.entrySet()) {
            if (cached.getKey().order == EntryOrder.COUNT && matches(entry, cached.getKey())) {
                cached.getValue().entries.add(entry);
            }
        }
    }

    private void applyViewTotalDelta(Entry entry, long delta) {
        if (delta == 0) {
            return;
        }
        for (Map.Entry<ViewKey, ViewState> cached : views.entrySet()) {
            if (matches(entry, cached.getKey())) {
                cached.getValue().totalItems = saturatedAdd(cached.getValue().totalItems, delta);
            }
        }
    }

    private NavigableSet<Entry> ordered(EntryOrder order) {
        return switch (order) {
            case COUNT -> byCount;
            case RECENT -> byInsertion.descendingSet();
            case INSERTION -> byInsertion;
            case REGISTRY -> byRegistry;
        };
    }

    private PageSlice paginate(NavigableSet<Entry> source, PageCursorCache cursors,
                               int page, int pageSize) {
        Iterator<Entry> iterator;
        int visited = 0;
        if (page == 0) {
            iterator = source.iterator();
        } else {
            Entry cursor = byId.get(cursors.startEntryIds.get(page));
            if (cursor != null && source.contains(cursor)) {
                iterator = source.tailSet(cursor, true).iterator();
            } else {
                iterator = source.iterator();
                int skip = page * pageSize;
                while (skip-- > 0 && iterator.hasNext()) {
                    iterator.next();
                    visited++;
                }
            }
        }
        List<EntryView> entries = new ArrayList<>(pageSize);
        while (entries.size() < pageSize && iterator.hasNext()) {
            Entry entry = iterator.next();
            entries.add(new EntryView(entry.id, entry.key, entry.count));
            visited++;
        }
        if (iterator.hasNext()) {
            Entry next = iterator.next();
            cursors.startEntryIds.put(page + 1, next.id);
        } else {
            cursors.startEntryIds.remove(page + 1);
        }
        return new PageSlice(entries, visited);
    }

    private ViewState buildView(ViewKey key) {
        long started = StorageMetrics.startTimer();
        List<Entry> matchedEntries = new ArrayList<>();
        long items = 0;
        int visited = 0;
        boolean categoryIndexed = !TerminalFilter.ALL.equals(key.category)
                && key.mode != TerminalFilter.CategoryMode.NONE;
        Collection<Entry> candidates = categoryCandidates(key);
        for (Entry entry : candidates) {
            visited++;
            if (matches(entry, key)) {
                matchedEntries.add(entry);
                items = saturatedAdd(items, entry.count);
            }
        }
        NavigableSet<Entry> result;
        if (matchedEntries.size() == byId.size()) {
            // TreeSet(SortedSet) uses Java's linear build-from-sorted path.
            // Common broad searches therefore avoid N logarithmic inserts.
            result = new TreeSet<>(ordered(key.order));
            StorageMetrics.increment("view_linear_full_matches");
        } else {
            result = new TreeSet<>(comparator(key.order));
            result.addAll(matchedEntries);
        }
        StorageMetrics.increment("view_builds");
        if (categoryIndexed) {
            StorageMetrics.increment("category_index_scans");
        } else {
            StorageMetrics.increment("full_scans");
        }
        StorageMetrics.add("view.entries_visited", visited);
        StorageMetrics.stopTimer("view_build", started);
        return new ViewState(result, items);
    }

    private boolean matches(Entry entry, ViewKey key) {
        if (!key.queryTokens.isEmpty()
                && !TerminalFilter.matchesQuery(entry.searchText(), key.queryTokens)) {
            return false;
        }
        return switch (key.mode) {
            case NONE -> true;
            case TYPE -> TerminalFilter.ALL.equals(key.category)
                    || (TerminalFilter.UNCATEGORIZED.equals(key.category)
                    ? entry.creativeCategories().isEmpty()
                    : entry.creativeCategories().contains(key.category));
            case MOD -> TerminalFilter.ALL.equals(key.category)
                    || entry.modCategory.equals(key.category);
        };
    }

    private ViewKey viewKey(EntryOrder order, TerminalFilter.Selection filter) {
        return new ViewKey(order, filter.query(), TerminalFilter.queryTokens(filter.query()),
                filter.mode(), filter.category());
    }

    private Collection<Entry> categoryCandidates(ViewKey key) {
        if (TerminalFilter.ALL.equals(key.category)
                || key.mode == TerminalFilter.CategoryMode.NONE) {
            return byId.values();
        }
        Map<String, LinkedHashSet<Entry>> index = key.mode == TerminalFilter.CategoryMode.MOD
                ? modCategoryEntries : creativeCategoryEntries;
        Collection<Entry> entries = index.get(key.category);
        return entries == null ? List.of() : entries;
    }

    private TerminalFilter.Selection validateFilter(TerminalFilter.Selection requested) {
        TerminalFilter.Selection safe = requested == null
                ? TerminalFilter.sanitize("", TerminalFilter.CategoryMode.NONE, TerminalFilter.ALL)
                : TerminalFilter.sanitize(requested.query(), requested.mode(), requested.category());
        List<String> categories = availableCategories(safe.mode());
        String category = categories.contains(safe.category()) ? safe.category() : TerminalFilter.ALL;
        return new TerminalFilter.Selection(safe.query(), safe.mode(), category);
    }

    private List<String> availableCategories(TerminalFilter.CategoryMode mode) {
        ensureCreativeCategoriesCurrent();
        if (mode == TerminalFilter.CategoryMode.NONE) {
            return List.of(TerminalFilter.ALL);
        }
        List<String> result = new ArrayList<>();
        result.add(TerminalFilter.ALL);
        if (mode == TerminalFilter.CategoryMode.TYPE) {
            for (String category : TerminalFilter.orderedCreativeCategories()) {
                if (creativeCategoryCounts.containsKey(category)) {
                    result.add(category);
                }
            }
            if (creativeCategoryCounts.containsKey(TerminalFilter.UNCATEGORIZED)) {
                result.add(TerminalFilter.UNCATEGORIZED);
            }
        } else {
            List<String> mods = new ArrayList<>(modCategoryCounts.keySet());
            mods.sort(String::compareTo);
            result.addAll(mods);
        }
        return List.copyOf(result);
    }

    private void ensureCreativeCategoriesCurrent() {
        int generation = TerminalFilter.creativeGeneration();
        if (creativeGeneration == generation) {
            return;
        }
        creativeGeneration = generation;
        stateEpoch++;
        pageSnapshots.clear();
        creativeCategoryCounts.clear();
        creativeCategoryEntries.clear();
        for (Entry entry : byId.values()) {
            entry.resetCreativeCategories();
            List<String> categories = entry.creativeCategories();
            if (categories.isEmpty()) {
                increment(creativeCategoryCounts, TerminalFilter.UNCATEGORIZED);
                addCategoryEntry(creativeCategoryEntries,
                        TerminalFilter.UNCATEGORIZED, entry);
            } else {
                categories.forEach(category -> {
                    increment(creativeCategoryCounts, category);
                    addCategoryEntry(creativeCategoryEntries, category, entry);
                });
            }
        }
        views.entrySet().removeIf(entry -> entry.getKey().mode == TerminalFilter.CategoryMode.TYPE);
    }

    private static Comparator<Entry> comparator(EntryOrder order) {
        return switch (order) {
            case REGISTRY -> registryComparator();
            case COUNT -> countComparator();
            case RECENT -> Comparator.comparingLong(Entry::id).reversed();
            case INSERTION -> Comparator.comparingLong(Entry::id);
        };
    }

    private static Comparator<Entry> registryComparator() {
        return Comparator.comparing((Entry entry) -> entry.registryId)
                .thenComparingLong(Entry::id);
    }

    private static Comparator<Entry> countComparator() {
        return Comparator.comparingLong((Entry entry) -> entry.count).reversed()
                .thenComparingLong(Entry::id);
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private static void decrement(Map<String, Integer> counts, String key) {
        counts.computeIfPresent(key, (ignored, value) -> value <= 1 ? null : value - 1);
    }

    private static void addCategoryEntry(
            Map<String, LinkedHashSet<Entry>> index, String category, Entry entry) {
        index.computeIfAbsent(category, ignored -> new LinkedHashSet<>()).add(entry);
    }

    private static void removeCategoryEntry(
            Map<String, LinkedHashSet<Entry>> index, String category, Entry entry) {
        LinkedHashSet<Entry> entries = index.get(category);
        if (entries == null) {
            return;
        }
        entries.remove(entry);
        if (entries.isEmpty()) {
            index.remove(category);
        }
    }

    private static long saturatedAdd(long value, long delta) {
        if (delta > 0 && value > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, value + delta);
    }

    private static final class Entry {
        private final long id;
        private final ItemKey key;
        private final ItemStack template;
        private final String registryId;
        private final String modCategory;
        private List<String> creativeCategories;
        private String searchText;
        private long count;

        private Entry(long id, ItemKey key, ItemStack template, long count) {
            this.id = id;
            this.key = key;
            this.template = template;
            this.registryId = BuiltInRegistries.ITEM.getKey(template.getItem()).toString();
            this.modCategory = TerminalFilter.modCategory(template);
            long metadataStarted = StorageMetrics.startTimer();
            this.searchText = TerminalFilter.searchText(template);
            StorageMetrics.stopTimer("entry_metadata_build", metadataStarted);
            this.count = count;
        }

        private long id() {
            return id;
        }

        private List<String> creativeCategories() {
            if (creativeCategories == null) {
                creativeCategories = TerminalFilter.creativeCategories(template);
            }
            return creativeCategories;
        }

        private void resetCreativeCategories() {
            creativeCategories = null;
        }

        private String searchText() {
            return searchText;
        }
    }

    private static final class ViewState {
        private final NavigableSet<Entry> entries;
        private final PageCursorCache pageCursors = new PageCursorCache();
        private long totalItems;
        private int references;

        private ViewState(NavigableSet<Entry> entries, long totalItems) {
            this.entries = entries;
            this.totalItems = totalItems;
        }
    }

    private static final class PageCursorCache {
        private final Map<Integer, Long> startEntryIds = new HashMap<>();

        private void clear() {
            startEntryIds.clear();
        }
    }

    private record PageSlice(List<EntryView> entries, int visited) {
    }

    private record ViewKey(EntryOrder order, String query, List<String> queryTokens,
                           TerminalFilter.CategoryMode mode, String category) {
    }

    private record PageKey(EntryOrder order, String query,
                           TerminalFilter.CategoryMode mode, String category,
                           int page, int pageSize, long epoch) {
    }

    public static final class EntryView {
        private final long id;
        private final ItemKey key;
        private final long count;

        private EntryView(long id, ItemKey key, long count) {
            this.id = id;
            this.key = key;
            this.count = count;
        }

        public long id() {
            return id;
        }

        public ItemStack stack() {
            StorageMetrics.increment("item_stack_copies");
            return key.toStack();
        }

        public long count() {
            return count;
        }
    }

    public record BrowseResult(List<EntryView> entries, int page, int pageCount,
                               int filteredTypes, long filteredItems,
                               List<String> categories, String selectedCategory,
                               long revision) {
    }

    public record SnapshotEntry(long id, ItemStack stack, long count) {
    }

    public static final class ViewLease implements AutoCloseable {
        private static final ViewLease EMPTY = new ViewLease(null, null);
        private StorageRuntime owner;
        private final ViewKey key;

        private ViewLease(StorageRuntime owner, ViewKey key) {
            this.owner = owner;
            this.key = key;
        }

        @Override
        public void close() {
            if (owner != null) {
                StorageRuntime current = owner;
                owner = null;
                current.releaseView(key);
            }
        }
    }

    public final class SnapshotCursor {
        private final Iterator<Entry> iterator;

        private SnapshotCursor(Iterator<Entry> iterator) {
            this.iterator = iterator;
        }

        public List<SnapshotEntry> next(int limit) {
            List<SnapshotEntry> batch = new ArrayList<>(Math.max(0, limit));
            while (batch.size() < limit && iterator.hasNext()) {
                Entry entry = iterator.next();
                batch.add(new SnapshotEntry(
                        entry.id, entry.template.copy(), entry.count));
            }
            return batch;
        }

        public boolean complete() {
            return !iterator.hasNext();
        }
    }

    public record ChangeSet(long contentRevision, long structureRevision,
                            long categoryRevision, Map<Long, Long> amountDeltas,
                            boolean structural) {
    }

    public enum EntryOrder {
        REGISTRY,
        COUNT,
        RECENT,
        INSERTION
    }

    @FunctionalInterface
    public interface Listener {
        Listener NONE = (runtime, changes) -> {
        };

        void onFlush(StorageRuntime runtime, ChangeSet changes);
    }
}
