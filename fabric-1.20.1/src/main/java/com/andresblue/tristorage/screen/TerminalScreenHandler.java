package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.NetworkBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.RemoteAccessHandle;
import com.andresblue.tristorage.storage.StorageMetrics;
import com.andresblue.tristorage.storage.TerminalFilter;
import com.andresblue.tristorage.storage.StorageRuntime;
import com.andresblue.tristorage.network.TerminalPackets;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TerminalScreenHandler extends ScreenHandler {
    public static final int PAGE_SIZE = 54;
    private static final int COUNT_WORDS = PropertyWords.LONG_WORDS;
    private static final int META_START = PAGE_SIZE * COUNT_WORDS;
    private static final int META_PAGE = META_START;
    private static final int META_PAGE_COUNT = META_PAGE + PropertyWords.INT_WORDS;
    private static final int META_FILTERED_TYPES = META_PAGE_COUNT + PropertyWords.INT_WORDS;
    private static final int META_FILTERED_ITEMS = META_FILTERED_TYPES + PropertyWords.INT_WORDS;
    private static final int META_SORT_MODE = META_FILTERED_ITEMS + PropertyWords.LONG_WORDS;
    private static final int META_STORED_TYPES = META_SORT_MODE + 1;
    private static final int PROPERTY_COUNT = META_STORED_TYPES + PropertyWords.INT_WORDS;
    protected static final int PLAYER_START = PAGE_SIZE;
    protected static final int PLAYER_END = PLAYER_START + 36;
    private static final int SORT_REGISTRY = 0;
    private static final int SORT_COUNT = 1;
    private static final int SORT_RECENT = 2;
    private static final long TICK_NANOS = 50_000_000L;
    private static final int FILTER_DUTY_FACTOR = 4;
    private static final int FILTER_MIN_INTERVAL_TICKS = 2;
    private static final int FILTER_MAX_INTERVAL_TICKS = 40;

    private final SimpleInventory display = new SimpleInventory(PAGE_SIZE);
    protected final StorageCoreBlockEntity core;
    private final boolean remote;
    private final RemoteAccessHandle remoteAccessHandle;
    private final ServerPlayerEntity serverPlayer;
    private final PropertyDelegate syncedProperties;
    private BlockPos accessPos;
    private final List<Long> entryIds = new ArrayList<>(Collections.nCopies(PAGE_SIZE, 0L));
    private final long[] counts = new long[PAGE_SIZE];
    private int page;
    private int sortMode;
    private int filterSequence;
    private int filteredPageCount = 1;
    private int filteredTypes;
    private long filteredItems;
    private TerminalFilter.Selection filter = TerminalFilter.sanitize(
            "", TerminalFilter.CategoryMode.NONE, TerminalFilter.ALL);
    private List<String> filterCategories = List.of(TerminalFilter.ALL);
    private long seenRevision = Long.MIN_VALUE;
    private boolean refreshRequested;
    private boolean pageStateDirty = true;
    private UUID clientStorageUuid;
    private long clientPageRevision = Long.MIN_VALUE;
    private final long[] clientEntryIds = new long[PAGE_SIZE];
    private boolean filterStateReady;
    private int sentFilterSequence = Integer.MIN_VALUE;
    private TerminalFilter.CategoryMode sentFilterMode;
    private String sentFilterCategory;
    private List<String> sentFilterCategories = List.of();
    private StorageRuntime.ViewLease viewLease;
    private StorageCoreBlockEntity.EntryOrder leasedViewOrder;
    private TerminalFilter.Selection leasedViewFilter;
    private PendingFilter pendingFilter;
    private long nextFilterTick = Long.MIN_VALUE;

    public TerminalScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null, null);
    }

    public TerminalScreenHandler(int syncId, PlayerInventory playerInventory,
                                 StorageCoreBlockEntity core) {
        this(syncId, playerInventory, core, null);
    }

    public TerminalScreenHandler(int syncId, PlayerInventory playerInventory,
                                 StorageCoreBlockEntity core, RemoteAccessHandle remoteAccessHandle) {
        this(TriStorageMod.TERMINAL_SCREEN_HANDLER, syncId, playerInventory,
                core, remoteAccessHandle);
    }

    protected TerminalScreenHandler(ScreenHandlerType<?> type, int syncId,
                                    PlayerInventory playerInventory,
                                    StorageCoreBlockEntity core,
                                    RemoteAccessHandle remoteAccessHandle) {
        super(type, syncId);
        this.core = core;
        this.remoteAccessHandle = remoteAccessHandle;
        this.remote = remoteAccessHandle != null;
        this.serverPlayer = playerInventory.player instanceof ServerPlayerEntity player
                ? player : null;
        if (serverPlayer != null) {
            TerminalFilter.prepareCreativeGroups(serverPlayer);
        }
        this.syncedProperties = core == null
                ? new ArrayPropertyDelegate(PROPERTY_COUNT)
                : properties(core);
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new DisplaySlot(display, column + row * 9,
                        8 + column * 18, 18 + row * 18));
            }
        }
        addPlayerInventory(playerInventory, 8, 140);
        addProperties(syncedProperties);
        refreshPage();
        filterStateReady = true;
    }

    @Override
    public void sendContentUpdates() {
        applyPendingFilter();
        if (core != null && (refreshRequested || seenRevision != core.revision())) {
            refreshPage();
            refreshRequested = false;
        }
        super.sendContentUpdates();
        if (pageStateDirty && serverPlayer != null) {
            TerminalPackets.sendPageState(serverPlayer, this);
            pageStateDirty = false;
        }
        // The filter acknowledgement is intentionally queued after vanilla's
        // slot/property packets. The client keeps virtual slots locked until
        // this arrives, preventing a click from targeting a stale item.
        sendFilterStateIfChanged();
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (core == null) {
            return false;
        }
        boolean pageOnly = true;
        switch (id) {
            case 0 -> page = Math.max(0, page - 1);
            case 1 -> page = Math.min(filteredPageCount - 1, page + 1);
            case 2 -> {
                depositPlayerInventory(player);
                refreshRequested = true;
                pageOnly = false;
            }
            case 3 -> {
                sortMode = (sortMode + 1) % 3;
                page = 0;
            }
            default -> {
                return false;
            }
        }
        if (pageOnly) {
            refreshPage();
            sendContentUpdates();
        }
        return true;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (core != null && actionType == SlotActionType.QUICK_MOVE
                && slotIndex >= 0 && slotIndex < slots.size()) {
            quickMove(player, slotIndex);
            return;
        }
        if (core != null && actionType == SlotActionType.PICKUP_ALL) {
            core.batchMutations(() -> {
                if (slotIndex >= 0 && slotIndex < PAGE_SIZE) {
                    withdrawAllMatching(entryIds.get(slotIndex), player);
                } else if (slotIndex >= PLAYER_START && slotIndex < PLAYER_END) {
                    depositAllMatching(player, slots.get(slotIndex).getStack());
                }
            });
            refreshRequested = true;
            return;
        }
        if (core != null && slotIndex >= 0 && slotIndex < PAGE_SIZE) {
            handleVirtualSlotAction(entryIds.get(slotIndex), button, actionType, player);
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (core == null || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = core.batchMutations(() -> quickMoveUnbatched(player, slotIndex));
        if (!result.isEmpty()) {
            refreshRequested = true;
        }
        return result;
    }

    private ItemStack quickMoveUnbatched(PlayerEntity player, int slotIndex) {
        if (slotIndex < PAGE_SIZE) {
            return quickMoveVirtual(player, entryIds.get(slotIndex));
        }

        Slot slot = slots.get(slotIndex);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        long inserted = core.insert(source, source.getCount());
        if (inserted <= 0) {
            return ItemStack.EMPTY;
        }
        source.decrement((int) inserted);
        slot.markDirty();
        return original;
    }

    private ItemStack quickMoveVirtual(PlayerEntity player, long entryId) {
            ItemStack extracted = extractEntry(entryId, 64);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack original = extracted.copy();
            if (!insertItem(extracted, PLAYER_START, PLAYER_END, true)) {
                returnToStorage(original, player);
                return ItemStack.EMPTY;
            }
            returnToStorage(extracted, player);
            return original;
    }

    /**
     * Records the network block the player used. Terminals reach a Core
     * through up to 64 network blocks, so reach is measured from the terminal
     * rather than from the Core.
     */
    public TerminalScreenHandler accessedFrom(BlockPos pos) {
        this.accessPos = pos.toImmutable();
        return this;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (core == null || core.isRemoved()) {
            return core == null;
        }
        if (remote) {
            return remoteAccessHandle.isValid();
        }
        BlockPos anchor = accessPos != null ? accessPos : core.getPos();
        if (accessPos != null && !(player.getWorld().getBlockState(accessPos).getBlock()
                instanceof NetworkBlock)) {
            return false;
        }
        return player.squaredDistanceTo(Vec3d.ofCenter(anchor)) <= 64.0;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (viewLease != null) {
            viewLease.close();
            viewLease = null;
        }
        if (remoteAccessHandle != null) {
            remoteAccessHandle.close();
        }
    }

    public int page() {
        return (int) PropertyWords.read(syncedProperties, META_PAGE, PropertyWords.INT_WORDS);
    }

    public int pageCount() {
        return Math.max(1, (int) PropertyWords.read(
                syncedProperties, META_PAGE_COUNT, PropertyWords.INT_WORDS));
    }

    public int storedTypes() {
        return (int) PropertyWords.read(
                syncedProperties, META_FILTERED_TYPES, PropertyWords.INT_WORDS);
    }

    public int totalStoredTypes() {
        return (int) PropertyWords.read(
                syncedProperties, META_STORED_TYPES, PropertyWords.INT_WORDS);
    }

    public long totalItems() {
        return PropertyWords.read(
                syncedProperties, META_FILTERED_ITEMS, PropertyWords.LONG_WORDS);
    }

    public int sortMode() {
        return (int) PropertyWords.read(syncedProperties, META_SORT_MODE, 1);
    }

    public void applyClientFilter(int sequence, String query, TerminalFilter.CategoryMode mode,
                                  String category) {
        if (core == null || serverPlayer == null) {
            return;
        }
        // Only the newest request matters: the client waits for the
        // acknowledgement of its latest sequence and ignores older ones.
        pendingFilter = new PendingFilter(sequence, query, mode, category);
        if (applyPendingFilter()) {
            sendContentUpdates();
        }
    }

    /**
     * Rebuilding a filtered view is O(types). Spacing rebuilds by their own
     * cost keeps a client that floods filter packets from monopolizing the
     * server thread, while small storages still answer within two ticks.
     */
    static int filterCooldownTicks(long rebuildNanos) {
        long ticks = (Math.max(0L, rebuildNanos) * FILTER_DUTY_FACTOR + TICK_NANOS - 1)
                / TICK_NANOS;
        return (int) Math.max(FILTER_MIN_INTERVAL_TICKS,
                Math.min(FILTER_MAX_INTERVAL_TICKS, ticks));
    }

    private boolean applyPendingFilter() {
        if (pendingFilter == null || serverPlayer == null) {
            return false;
        }
        long now = serverPlayer.getServer().getTicks();
        if (now < nextFilterTick) {
            return false;
        }
        PendingFilter request = pendingFilter;
        pendingFilter = null;
        long started = System.nanoTime();
        filterSequence = request.sequence();
        filter = TerminalFilter.sanitize(request.query(), request.mode(), request.category());
        page = 0;
        refreshPage();
        nextFilterTick = now + filterCooldownTicks(System.nanoTime() - started);
        return true;
    }

    public List<String> filterCategories() {
        return filterCategories;
    }

    public String selectedFilterCategory() {
        return filter.category();
    }

    public TerminalFilter.CategoryMode filterMode() {
        return filter.mode();
    }

    public int filterSequence() {
        return filterSequence;
    }

    public long displayCount(int slot) {
        if (slot < 0 || slot >= PAGE_SIZE) {
            return 0;
        }
        return PropertyWords.read(syncedProperties, slot * COUNT_WORDS, COUNT_WORDS);
    }

    /** Puts items back into storage, or gives them to the player if the Core refuses them. */
    private void returnToStorage(ItemStack stack, PlayerEntity player) {
        if (stack.isEmpty()) {
            return;
        }
        long inserted = core.insert(stack, stack.getCount());
        stack.decrement((int) inserted);
        if (!stack.isEmpty()) {
            player.getInventory().offerOrDrop(stack);
        }
    }

    private ItemStack extractSlot(int slotIndex, int requested) {
        return extractEntry(entryIds.get(slotIndex), requested);
    }

    private ItemStack extractEntry(long entryId, int requested) {
        return entryId == 0 ? ItemStack.EMPTY : core.extract(entryId, requested);
    }

    private void withdrawAllMatching(long entryId, PlayerEntity player) {
        ItemStack shown = core.stackTemplate(entryId);
        if (entryId == 0 || shown.isEmpty()) {
            return;
        }

        ItemStack cursor = getCursorStack();
        if (!cursor.isEmpty()) {
            if (!ItemStack.canCombine(cursor, shown)) {
                return;
            }
            player.getInventory().insertStack(cursor);
            setCursorStack(cursor);
            if (!cursor.isEmpty()) {
                return;
            }
        }

        while (true) {
            ItemStack extracted = core.extract(entryId, shown.getMaxCount());
            if (extracted.isEmpty()) {
                break;
            }
            player.getInventory().insertStack(extracted);
            if (!extracted.isEmpty()) {
                returnToStorage(extracted, player);
                break;
            }
        }
        player.getInventory().markDirty();
    }

    private void depositAllMatching(PlayerEntity player, ItemStack clickedStack) {
        ItemStack cursor = getCursorStack();
        ItemStack reference = (!cursor.isEmpty() ? cursor : clickedStack).copy();
        if (reference.isEmpty()) {
            return;
        }
        reference.setCount(1);

        if (!cursor.isEmpty() && ItemStack.canCombine(cursor, reference)) {
            long inserted = core.insert(cursor, cursor.getCount());
            cursor.decrement((int) inserted);
            setCursorStack(cursor);
        }
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = player.getInventory().getStack(inventorySlot);
            if (stack.isEmpty() || !ItemStack.canCombine(stack, reference)) {
                continue;
            }
            long inserted = core.insert(stack, stack.getCount());
            stack.decrement((int) inserted);
        }
        player.getInventory().markDirty();
    }

    private void depositPlayerInventory(PlayerEntity player) {
        core.batchMutations(() -> {
            boolean inventoryChanged = false;
            for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
                ItemStack stack = player.getInventory().getStack(inventorySlot);
                if (stack.isEmpty()) {
                    continue;
                }
                long inserted = core.insert(stack, stack.getCount());
                if (inserted > 0) {
                    stack.decrement((int) inserted);
                    inventoryChanged = true;
                }
            }
            if (inventoryChanged) {
                player.getInventory().markDirty();
            }
        });
    }

    protected final void refreshPage() {
        if (core == null) {
            return;
        }
        long refreshStarted = StorageMetrics.startTimer();
        StorageMetrics.increment("terminal.page_refreshes");
        StorageCoreBlockEntity.EntryOrder order = switch (sortMode) {
            case SORT_COUNT -> StorageCoreBlockEntity.EntryOrder.COUNT;
            case SORT_RECENT -> StorageCoreBlockEntity.EntryOrder.RECENT;
            default -> StorageCoreBlockEntity.EntryOrder.REGISTRY;
        };
        StorageRuntime.BrowseResult result = core.browseRuntime(
                page, PAGE_SIZE, order, filter);
        page = result.page();
        filteredPageCount = result.pageCount();
        filteredTypes = result.filteredTypes();
        filteredItems = result.filteredItems();
        filterCategories = result.categories();
        filter = new TerminalFilter.Selection(
                filter.query(), filter.mode(), result.selectedCategory());
        if (viewLease == null || leasedViewOrder != order
                || !filter.equals(leasedViewFilter)) {
            StorageRuntime.ViewLease nextViewLease = core.retainView(order, filter);
            if (viewLease != null) {
                viewLease.close();
            }
            viewLease = nextViewLease;
            leasedViewOrder = order;
            leasedViewFilter = filter;
        }
        List<StorageRuntime.EntryView> views = result.entries();
        for (int index = 0; index < PAGE_SIZE; index++) {
            if (index < views.size()) {
                StorageRuntime.EntryView view = views.get(index);
                if (entryIds.get(index) != view.id()) {
                    ItemStack shown = view.stack();
                    shown.setCount(1);
                    display.setStack(index, shown);
                }
                entryIds.set(index, view.id());
                counts[index] = view.count();
            } else {
                if (entryIds.get(index) != 0L) {
                    display.setStack(index, ItemStack.EMPTY);
                }
                entryIds.set(index, 0L);
                counts[index] = 0;
            }
        }
        seenRevision = result.revision();
        pageStateDirty = true;
        StorageMetrics.stopTimer("terminal.page_refresh", refreshStarted);
    }

    protected final void requestStorageRefresh() {
        refreshRequested = true;
    }

    /**
     * Handles a virtual slot action identified by stable storage identity,
     * EntryId and the page revision seen by the client. A stale packet can
     * only request a resync; it can never target the entry currently occupying
     * the old slot.
     */
    public void applyVirtualAction(ServerPlayerEntity player, UUID storageId,
                                   long clientRevision, int slotIndex, long entryId,
                                   int button, SlotActionType actionType) {
        if (core == null || player != serverPlayer || slotIndex < 0 || slotIndex >= PAGE_SIZE
                || !core.storageId().value().equals(storageId)
                || clientRevision != seenRevision || !canUse(player)) {
            refreshRequested = true;
            sendContentUpdates();
            if (!canUse(player)) {
                player.closeHandledScreen();
            }
            return;
        }
        if (actionType == SlotActionType.QUICK_MOVE) {
            ItemStack moved = core.batchMutations(() -> quickMoveVirtual(player, entryId));
            if (!moved.isEmpty()) {
                refreshRequested = true;
            }
        } else {
            handleVirtualSlotAction(entryId, button, actionType, player);
        }
    }

    private void handleVirtualSlotAction(long entryId, int button,
                                         SlotActionType actionType, PlayerEntity player) {
        if (actionType == SlotActionType.PICKUP) {
            ItemStack cursor = getCursorStack();
            if (cursor.isEmpty()) {
                ItemStack extracted = extractEntry(entryId, button == 1 ? 1 : 64);
                if (!extracted.isEmpty()) {
                    setCursorStack(extracted);
                }
            } else {
                long requested = button == 1 ? 1 : cursor.getCount();
                long inserted = core.insert(cursor, requested);
                cursor.decrement((int) inserted);
                setCursorStack(cursor);
            }
            refreshRequested = true;
            return;
        }
        if (actionType == SlotActionType.PICKUP_ALL) {
            core.batchMutations(() -> withdrawAllMatching(entryId, player));
            refreshRequested = true;
            return;
        }
        if (actionType == SlotActionType.THROW) {
            ItemStack extracted = extractEntry(entryId, button == 0 ? 1 : 64);
            if (!extracted.isEmpty()) {
                player.dropItem(extracted, true);
            }
            refreshRequested = true;
            return;
        }
        if (actionType == SlotActionType.CLONE && player.getAbilities().creativeMode) {
            ItemStack shown = core.stackTemplate(entryId);
            if (!shown.isEmpty()) {
                shown.setCount(shown.getMaxCount());
                setCursorStack(shown);
            }
        }
        // Display slots are projections. SWAP/QUICK_CRAFT must never mutate
        // the backing SimpleInventory.
    }

    public UUID storageUuid() {
        return core.storageId().value();
    }

    public long serverPageRevision() {
        return seenRevision;
    }

    public long serverEntryId(int slot) {
        return slot >= 0 && slot < PAGE_SIZE ? entryIds.get(slot) : 0;
    }

    public void acceptClientPageState(UUID storageId, long revision, long[] ids) {
        clientStorageUuid = storageId;
        clientPageRevision = revision;
        System.arraycopy(ids, 0, clientEntryIds, 0,
                Math.min(ids.length, clientEntryIds.length));
    }

    public UUID clientStorageUuid() {
        return clientStorageUuid;
    }

    public long clientPageRevision() {
        return clientPageRevision;
    }

    public long clientEntryId(int slot) {
        return slot >= 0 && slot < PAGE_SIZE ? clientEntryIds[slot] : 0;
    }

    public boolean hasAuthoritativePageState() {
        return clientStorageUuid != null && clientPageRevision != Long.MIN_VALUE;
    }

    private void sendFilterStateIfChanged() {
        if (!filterStateReady || serverPlayer == null) {
            return;
        }
        if (sentFilterSequence == filterSequence
                && sentFilterMode == filter.mode()
                && java.util.Objects.equals(sentFilterCategory, filter.category())
                && sentFilterCategories.equals(filterCategories)) {
            return;
        }
        sentFilterSequence = filterSequence;
        sentFilterMode = filter.mode();
        sentFilterCategory = filter.category();
        sentFilterCategories = List.copyOf(filterCategories);
        TerminalPackets.sendFilterState(serverPlayer, this);
    }

    private PropertyDelegate properties(StorageCoreBlockEntity core) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                if (index < META_START) {
                    return PropertyWords.word(counts[index / COUNT_WORDS], index % COUNT_WORDS);
                }
                if (index < META_PAGE_COUNT) {
                    return PropertyWords.word(page, index - META_PAGE);
                }
                if (index < META_FILTERED_TYPES) {
                    return PropertyWords.word(filteredPageCount, index - META_PAGE_COUNT);
                }
                if (index < META_FILTERED_ITEMS) {
                    return PropertyWords.word(filteredTypes, index - META_FILTERED_TYPES);
                }
                if (index < META_SORT_MODE) {
                    return PropertyWords.word(filteredItems, index - META_FILTERED_ITEMS);
                }
                if (index == META_SORT_MODE) {
                    return PropertyWords.word(sortMode, 0);
                }
                if (index < PROPERTY_COUNT) {
                    return PropertyWords.word(core.storedTypes(), index - META_STORED_TYPES);
                }
                return 0;
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int size() {
                return PROPERTY_COUNT;
            }
        };
    }

    private void addPlayerInventory(PlayerInventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        x + column * 18, y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, x + column * 18, y + 58));
        }
    }

    private record PendingFilter(int sequence, String query, TerminalFilter.CategoryMode mode,
                                 String category) {
    }

    private static final class DisplaySlot extends Slot {
        private DisplaySlot(SimpleInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            // The slot is a read-only projection, but it must advertise that it
            // can be taken from so vanilla sends QUICK_MOVE/PICKUP_ALL packets.
            // The server-side handler performs the real extraction atomically.
            return true;
        }
    }
}
