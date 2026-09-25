package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.network.TerminalPackets;
import com.andresblue.tristorage.storage.RemoteAccessHandle;
import com.andresblue.tristorage.storage.TerminalFilter;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TerminalScreenHandler extends AbstractContainerMenu {
    public static final int PAGE_SIZE = 54;
    private static final int META_START = PAGE_SIZE * 2;
    private static final int PROPERTY_COUNT = META_START + 7;
    protected static final int PLAYER_START = PAGE_SIZE;
    protected static final int PLAYER_END = PLAYER_START + 36;
    private static final int SORT_REGISTRY = 0;
    private static final int SORT_COUNT = 1;
    private static final int SORT_RECENT = 2;

    private final SimpleContainer display = new SimpleContainer(PAGE_SIZE);
    protected final StorageCoreBlockEntity core;
    private final boolean remote;
    private final RemoteAccessHandle remoteAccessHandle;
    private final ServerPlayer serverPlayer;
    private final ContainerData syncedProperties;
    private final List<String> keys = new ArrayList<>(Collections.nCopies(PAGE_SIZE, ""));
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
    private int seenRevision = Integer.MIN_VALUE;
    private boolean filterStateReady;
    private String lastFilterState = "";

    public TerminalScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, null, null);
    }

    public TerminalScreenHandler(int syncId, Inventory playerInventory,
                                 StorageCoreBlockEntity core) {
        this(syncId, playerInventory, core, null);
    }

    public TerminalScreenHandler(int syncId, Inventory playerInventory,
                                 StorageCoreBlockEntity core, RemoteAccessHandle remoteAccessHandle) {
        this(TriStorageMod.TERMINAL_SCREEN_HANDLER.get(), syncId, playerInventory,
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
        if (core != null && seenRevision != core.revision()) {
            refreshPage();
        }
        super.broadcastChanges();
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
        switch (id) {
            case 0 -> page = Math.max(0, page - 1);
            case 1 -> page = Math.min(filteredPageCount - 1, page + 1);
            case 2 -> depositPlayerInventory(player);
            case 3 -> {
                sortMode = (sortMode + 1) % 3;
                page = 0;
            }
            default -> {
                return false;
            }
        }
        refreshPage();
        broadcastChanges();
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
                    withdrawAllMatching(slotIndex, player);
                } else if (slotIndex >= PLAYER_START && slotIndex < PLAYER_END) {
                    depositAllMatching(player, slots.get(slotIndex).getItem());
                }
            });
            refreshPage();
            return;
        }
        if (core != null && slotIndex >= 0 && slotIndex < PAGE_SIZE) {
            if (actionType == ClickType.PICKUP) {
                ItemStack cursor = getCarried();
                if (cursor.isEmpty()) {
                    int requested = button == 1 ? 1 : 64;
                    ItemStack extracted = extractSlot(slotIndex, requested);
                    if (!extracted.isEmpty()) {
                        setCarried(extracted);
                    }
                } else {
                    long requested = button == 1 ? 1 : cursor.getCount();
                    long inserted = core.insert(cursor, requested);
                    cursor.shrink((int) inserted);
                    setCarried(cursor);
                }
                refreshPage();
                return;
            }
            if (actionType == ClickType.THROW) {
                ItemStack extracted = extractSlot(slotIndex, button == 0 ? 1 : 64);
                if (!extracted.isEmpty()) {
                    player.drop(extracted, true);
                }
                refreshPage();
                return;
            }
            if (actionType == ClickType.CLONE && player.getAbilities().instabuild) {
                ItemStack shown = slots.get(slotIndex).getItem().copy();
                if (!shown.isEmpty()) {
                    shown.setCount(shown.getMaxStackSize());
                    setCarried(shown);
                }
                return;
            }
            // Display slots are virtual projections of the core. Never allow
            // vanilla SWAP/QUICK_CRAFT fallbacks to mutate their SimpleContainer.
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (core == null || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        int revisionBefore = core.revision();
        ItemStack result = core.batchMutations(() -> quickMoveUnbatched(player, slotIndex));
        if (core.revision() != revisionBefore) {
            refreshPage();
        }
        return result;
    }

    private ItemStack quickMoveUnbatched(Player player, int slotIndex) {
        if (slotIndex < PAGE_SIZE) {
            ItemStack extracted = extractSlot(slotIndex, 64);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack original = extracted.copy();
            if (!moveItemStackTo(extracted, PLAYER_START, PLAYER_END, true)) {
                core.insert(original, original.getCount());
                return ItemStack.EMPTY;
            }
            if (!extracted.isEmpty()) {
                core.insert(extracted, extracted.getCount());
            }
            return original;
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

    @Override
    public boolean stillValid(Player player) {
        if (core == null || core.isRemoved()) {
            return core == null;
        }
        return (remote && remoteAccessHandle.isValid()) || (!remote && player.distanceToSqr(
                core.getBlockPos().getX() + 0.5,
                core.getBlockPos().getY() + 0.5,
                core.getBlockPos().getZ() + 0.5) <= 64.0);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
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
        broadcastChanges();
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
        String key = keys.get(slotIndex);
        return key.isEmpty() ? ItemStack.EMPTY : core.extract(key, requested);
    }

    private void withdrawAllMatching(int slotIndex, Player player) {
        String key = keys.get(slotIndex);
        ItemStack shown = slots.get(slotIndex).getItem();
        if (key.isEmpty() || shown.isEmpty()) {
            return;
        }

        ItemStack cursor = getCarried();
        if (!cursor.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(cursor, shown)) {
                return;
            }
            player.getInventory().add(cursor);
            setCarried(cursor);
            if (!cursor.isEmpty()) {
                return;
            }
        }

        while (true) {
            ItemStack extracted = core.extract(key, shown.getMaxStackSize());
            if (extracted.isEmpty()) {
                break;
            }
            player.getInventory().add(extracted);
            if (!extracted.isEmpty()) {
                core.insert(extracted, extracted.getCount());
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

        if (!cursor.isEmpty() && ItemStack.isSameItemSameComponents(cursor, reference)) {
            long inserted = core.insert(cursor, cursor.getCount());
            cursor.shrink((int) inserted);
            setCarried(cursor);
        }
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = player.getInventory().getItem(inventorySlot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, reference)) {
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
        StorageCoreBlockEntity.EntryOrder order = switch (sortMode) {
            case SORT_COUNT -> StorageCoreBlockEntity.EntryOrder.COUNT;
            case SORT_RECENT -> StorageCoreBlockEntity.EntryOrder.RECENT;
            default -> StorageCoreBlockEntity.EntryOrder.REGISTRY;
        };
        StorageCoreBlockEntity.BrowseResult result = core.browse(page, PAGE_SIZE, order, filter);
        page = result.page();
        filteredPageCount = result.pageCount();
        filteredTypes = result.filteredTypes();
        filteredItems = result.filteredItems();
        filterCategories = result.categories();
        filter = new TerminalFilter.Selection(
                filter.query(), filter.mode(), result.selectedCategory());
        List<StorageCoreBlockEntity.EntryView> views = result.entries();
        for (int index = 0; index < PAGE_SIZE; index++) {
            if (index < views.size()) {
                StorageCoreBlockEntity.EntryView view = views.get(index);
                display.setItem(index, view.stack().copyWithCount(1));
                keys.set(index, view.key());
                counts[index] = view.count();
            } else {
                display.setItem(index, ItemStack.EMPTY);
                keys.set(index, "");
                counts[index] = 0;
            }
        }
        seenRevision = core.revision();
    }

    private void sendFilterStateIfChanged() {
        if (!filterStateReady || serverPlayer == null) {
            return;
        }
        String state = filterSequence + "\u0000" + filter.mode().name()
                + '\u0000' + filter.category()
                + '\u0000' + String.join("\u0001", filterCategories);
        if (state.equals(lastFilterState)) {
            return;
        }
        lastFilterState = state;
        TerminalPackets.sendFilterState(serverPlayer, this);
    }

    private ContainerData properties(StorageCoreBlockEntity core) {
        return new ContainerData() {
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

    private static final class DisplaySlot extends Slot {
        private DisplaySlot(SimpleContainer inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            // The slot is a read-only projection, but it must advertise that it
            // can be taken from so vanilla sends QUICK_MOVE/PICKUP_ALL packets.
            // The server-side handler performs the real extraction atomically.
            return true;
        }
    }
}
