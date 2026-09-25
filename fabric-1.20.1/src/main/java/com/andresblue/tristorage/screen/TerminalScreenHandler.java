package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TerminalScreenHandler extends ScreenHandler {
    public static final int PAGE_SIZE = 54;
    private static final int META_START = PAGE_SIZE * 2;
    private static final int PROPERTY_COUNT = META_START + 7;
    protected static final int PLAYER_START = PAGE_SIZE;
    protected static final int PLAYER_END = PLAYER_START + 36;
    private static final int SORT_REGISTRY = 0;
    private static final int SORT_COUNT = 1;
    private static final int SORT_RECENT = 2;

    private final SimpleInventory display = new SimpleInventory(PAGE_SIZE);
    protected final StorageCoreBlockEntity core;
    private final boolean remote;
    private final RemoteAccessHandle remoteAccessHandle;
    private final ServerPlayerEntity serverPlayer;
    private final PropertyDelegate syncedProperties;
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
                core.insert(original, original.getCount());
                return ItemStack.EMPTY;
            }
            if (!extracted.isEmpty()) {
                core.insert(extracted, extracted.getCount());
            }
            return original;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (core == null || core.isRemoved()) {
            return core == null;
        }
        return (remote && remoteAccessHandle.isValid()) || (!remote && player.squaredDistanceTo(
                core.getPos().getX() + 0.5,
                core.getPos().getY() + 0.5,
                core.getPos().getZ() + 0.5) <= 64.0);
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
        return syncedProperties.get(META_START);
    }

    public int pageCount() {
        return Math.max(1, syncedProperties.get(META_START + 1));
    }

    public int storedTypes() {
        return syncedProperties.get(META_START + 2);
    }

    public int totalStoredTypes() {
        return syncedProperties.get(META_START + 6);
    }

    public long totalItems() {
        return Integer.toUnsignedLong(syncedProperties.get(META_START + 3))
                | (Integer.toUnsignedLong(syncedProperties.get(META_START + 4)) << 32);
    }

    public int sortMode() {
        return syncedProperties.get(META_START + 5);
    }

    public void applyClientFilter(int sequence, String query, TerminalFilter.CategoryMode mode,
                                  String category) {
        if (core == null || serverPlayer == null) {
            return;
        }
        filterSequence = sequence;
        filter = TerminalFilter.sanitize(query, mode, category);
        page = 0;
        refreshPage();
        sendContentUpdates();
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
        return Integer.toUnsignedLong(syncedProperties.get(slot))
                | (Integer.toUnsignedLong(syncedProperties.get(PAGE_SIZE + slot)) << 32);
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
                core.insert(extracted, extracted.getCount());
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
                if (index < PAGE_SIZE) {
                    return (int) counts[index];
                }
                if (index < META_START) {
                    return (int) (counts[index - PAGE_SIZE] >>> 32);
                }
                return switch (index - META_START) {
                    case 0 -> page;
                    case 1 -> filteredPageCount;
                    case 2 -> filteredTypes;
                    case 3 -> (int) filteredItems;
                    case 4 -> (int) (filteredItems >>> 32);
                    case 5 -> sortMode;
                    case 6 -> core.storedTypes();
                    default -> 0;
                };
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
