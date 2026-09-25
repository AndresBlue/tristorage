package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.NetworkBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.RemoteAccessHandle;
import com.andresblue.tristorage.storage.StorageMetrics;
import com.andresblue.tristorage.storage.TerminalFilter;
import com.andresblue.tristorage.storage.StorageRuntime;
import com.andresblue.tristorage.network.TerminalPackets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class TerminalScreenHandler extends AbstractContainerMenu {
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

    private final SimpleContainer display = new SimpleContainer(PAGE_SIZE);
    protected final StorageCoreBlockEntity core;
    private final boolean remote;
    private final RemoteAccessHandle remoteAccessHandle;
    private final ServerPlayer serverPlayer;
    private final ContainerData syncedProperties;
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

    public TerminalScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, null, null);
    }

    public TerminalScreenHandler(int syncId, Inventory playerInventory,
                                 StorageCoreBlockEntity core) {
        this(syncId, playerInventory, core, null);
    }

    public TerminalScreenHandler(int syncId, Inventory playerInventory,
                                 StorageCoreBlockEntity core, RemoteAccessHandle remoteAccessHandle) {
        this(TriStorageMod.TERMINAL_SCREEN_HANDLER, syncId, playerInventory,
                core, remoteAccessHandle);
    }

    protected TerminalScreenHandler(MenuType<?> type, int syncId,
                                    Inventory playerInventory,
                                    StorageCoreBlockEntity core,
                                    RemoteAccessHandle remoteAccessHandle) {
        super(type, syncId);
        this.core = core;
        this.remoteAccessHandle = remoteAccessHandle;
        this.remote = remoteAccessHandle != null;
        this.serverPlayer = playerInventory.player instanceof ServerPlayer player
                ? player : null;
        if (serverPlayer != null) {
            TerminalFilter.prepareCreativeGroups(serverPlayer);
        }
        this.syncedProperties = core == null
                ? new SimpleContainerData(PROPERTY_COUNT)
                : properties(core);
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new DisplaySlot(display, column + row * 9,
                        8 + column * 18, 18 + row * 18));
            }
        }
        addPlayerInventory(playerInventory, 8, 140);
        addDataSlots(syncedProperties);
        refreshPage();
        filterStateReady = true;
    }

    @Override
    public void broadcastChanges() {
        applyPendingFilter();
        if (core != null && (refreshRequested || seenRevision != core.revision())) {
            refreshPage();
            refreshRequested = false;
        }
        super.broadcastChanges();
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
    public boolean clickMenuButton(Player player, int id) {
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
            broadcastChanges();
        }
        return true;
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType actionType, Player player) {
        if (core != null && actionType == ClickType.QUICK_MOVE
                && slotIndex >= 0 && slotIndex < slots.size()) {
            quickMoveStack(player, slotIndex);
            return;
        }
        if (core != null && actionType == ClickType.PICKUP_ALL) {
            core.batchMutations(() -> {
                if (slotIndex >= 0 && slotIndex < PAGE_SIZE) {
                    withdrawAllMatching(entryIds.get(slotIndex), player);
                } else if (slotIndex >= PLAYER_START && slotIndex < PLAYER_END) {
                    depositAllMatching(player, slots.get(slotIndex).getItem());
                }
            });
            refreshRequested = true;
            return;
        }
        if (core != null && slotIndex >= 0 && slotIndex < PAGE_SIZE) {
            handleVirtualSlotAction(entryIds.get(slotIndex), button, actionType, player);
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (core == null || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = core.batchMutations(() -> quickMoveUnbatched(player, slotIndex));
        if (!result.isEmpty()) {
            refreshRequested = true;
        }
        return result;
    }

    private ItemStack quickMoveUnbatched(Player player, int slotIndex) {
        if (slotIndex < PAGE_SIZE) {
            return quickMoveVirtual(player, entryIds.get(slotIndex));
        }

        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        long inserted = core.insert(source, source.getCount());
        if (inserted <= 0) {
            return ItemStack.EMPTY;
        }
        source.shrink((int) inserted);
        slot.setChanged();
        return original;
    }

    private ItemStack quickMoveVirtual(Player player, long entryId) {
            ItemStack extracted = extractEntry(entryId, 64);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack original = extracted.copy();
            if (!moveItemStackTo(extracted, PLAYER_START, PLAYER_END, true)) {
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
        this.accessPos = pos.immutable();
        return this;
    }

    @Override
    public boolean stillValid(Player player) {
        if (core == null || core.isRemoved()) {
            return core == null;
        }
        if (remote) {
            return remoteAccessHandle.isValid();
        }
        BlockPos anchor = accessPos != null ? accessPos : core.getBlockPos();
        if (accessPos != null && !(player.level().getBlockState(accessPos).getBlock()
                instanceof NetworkBlock)) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(anchor)) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
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
            broadcastChanges();
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
        long now = serverPlayer.getServer().getTickCount();
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
    private void returnToStorage(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return;
        }
        long inserted = core.insert(stack, stack.getCount());
        stack.shrink((int) inserted);
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    private ItemStack extractSlot(int slotIndex, int requested) {
        return extractEntry(entryIds.get(slotIndex), requested);
    }

    private ItemStack extractEntry(long entryId, int requested) {
        return entryId == 0 ? ItemStack.EMPTY : core.extract(entryId, requested);
    }

    private void withdrawAllMatching(long entryId, Player player) {
        ItemStack shown = core.stackTemplate(entryId);
        if (entryId == 0 || shown.isEmpty()) {
            return;
        }

        ItemStack cursor = getCarried();
        if (!cursor.isEmpty()) {
            if (!ItemStack.isSameItemSameTags(cursor, shown)) {
                return;
            }
            player.getInventory().add(cursor);
            setCarried(cursor);
            if (!cursor.isEmpty()) {
                return;
            }
        }

        while (true) {
            ItemStack extracted = core.extract(entryId, shown.getMaxStackSize());
            if (extracted.isEmpty()) {
                break;
            }
            player.getInventory().add(extracted);
            if (!extracted.isEmpty()) {
                returnToStorage(extracted, player);
                break;
            }
        }
        player.getInventory().setChanged();
    }

    private void depositAllMatching(Player player, ItemStack clickedStack) {
        ItemStack cursor = getCarried();
        ItemStack reference = (!cursor.isEmpty() ? cursor : clickedStack).copy();
        if (reference.isEmpty()) {
            return;
        }
        reference.setCount(1);

        if (!cursor.isEmpty() && ItemStack.isSameItemSameTags(cursor, reference)) {
            long inserted = core.insert(cursor, cursor.getCount());
            cursor.shrink((int) inserted);
            setCarried(cursor);
        }
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = player.getInventory().getItem(inventorySlot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, reference)) {
                continue;
            }
            long inserted = core.insert(stack, stack.getCount());
            stack.shrink((int) inserted);
        }
        player.getInventory().setChanged();
    }

    private void depositPlayerInventory(Player player) {
        core.batchMutations(() -> {
            boolean inventoryChanged = false;
            for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
                ItemStack stack = player.getInventory().getItem(inventorySlot);
                if (stack.isEmpty()) {
                    continue;
                }
                long inserted = core.insert(stack, stack.getCount());
                if (inserted > 0) {
                    stack.shrink((int) inserted);
                    inventoryChanged = true;
                }
            }
            if (inventoryChanged) {
                player.getInventory().setChanged();
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
                    display.setItem(index, shown);
                }
                entryIds.set(index, view.id());
                counts[index] = view.count();
            } else {
                if (entryIds.get(index) != 0L) {
                    display.setItem(index, ItemStack.EMPTY);
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
    public void applyVirtualAction(ServerPlayer player, UUID storageId,
                                   long clientRevision, int slotIndex, long entryId,
                                   int button, ClickType actionType) {
        if (core == null || player != serverPlayer || slotIndex < 0 || slotIndex >= PAGE_SIZE
                || !core.storageId().value().equals(storageId)
                || clientRevision != seenRevision || !stillValid(player)) {
            refreshRequested = true;
            broadcastChanges();
            if (!stillValid(player)) {
                player.closeContainer();
            }
            return;
        }
        if (actionType == ClickType.QUICK_MOVE) {
            ItemStack moved = core.batchMutations(() -> quickMoveVirtual(player, entryId));
            if (!moved.isEmpty()) {
                refreshRequested = true;
            }
        } else {
            handleVirtualSlotAction(entryId, button, actionType, player);
        }
    }

    private void handleVirtualSlotAction(long entryId, int button,
                                         ClickType actionType, Player player) {
        if (actionType == ClickType.PICKUP) {
            ItemStack cursor = getCarried();
            if (cursor.isEmpty()) {
                ItemStack extracted = extractEntry(entryId, button == 1 ? 1 : 64);
                if (!extracted.isEmpty()) {
                    setCarried(extracted);
                }
            } else {
                long requested = button == 1 ? 1 : cursor.getCount();
                long inserted = core.insert(cursor, requested);
                cursor.shrink((int) inserted);
                setCarried(cursor);
            }
            refreshRequested = true;
            return;
        }
        if (actionType == ClickType.PICKUP_ALL) {
            core.batchMutations(() -> withdrawAllMatching(entryId, player));
            refreshRequested = true;
            return;
        }
        if (actionType == ClickType.THROW) {
            ItemStack extracted = extractEntry(entryId, button == 0 ? 1 : 64);
            if (!extracted.isEmpty()) {
                player.drop(extracted, true);
            }
            refreshRequested = true;
            return;
        }
        if (actionType == ClickType.CLONE && player.getAbilities().instabuild) {
            ItemStack shown = core.stackTemplate(entryId);
            if (!shown.isEmpty()) {
                shown.setCount(shown.getMaxStackSize());
                setCarried(shown);
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

    private ContainerData properties(StorageCoreBlockEntity core) {
        return new ContainerData() {
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
            public int getCount() {
                return PROPERTY_COUNT;
            }
        };
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
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
        private DisplaySlot(SimpleContainer inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player playerEntity) {
            // The slot is a read-only projection, but it must advertise that it
            // can be taken from so vanilla sends QUICK_MOVE/PICKUP_ALL packets.
            // The server-side handler performs the real extraction atomically.
            return true;
        }
    }
}
