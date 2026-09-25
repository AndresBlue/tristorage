package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.RemoteAccessHandle;
import com.andresblue.tristorage.storage.StorageCategory;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TerminalScreenHandler extends ScreenHandler {
    public static final int PAGE_SIZE = 54;
    public static final int CATEGORY_WINDOW = 7;
    public static final int CATEGORY_START = PAGE_SIZE;
    public static final int PLAYER_START = PAGE_SIZE + CATEGORY_WINDOW;
    private static final int PROPERTY_COUNT = PAGE_SIZE + 9;

    private final SimpleInventory display = new SimpleInventory(PLAYER_START);
    protected final StorageCoreBlockEntity core;
    private final boolean remote;
    private final RemoteAccessHandle remoteAccessHandle;
    private final PropertyDelegate syncedProperties;
    private final List<String> keys = new ArrayList<>(Collections.nCopies(PAGE_SIZE, ""));
    private final long[] counts = new long[PAGE_SIZE];
    private List<StorageCategory> categories = List.of();
    private int page;
    private int categoryIndex;
    private int categoryWindowStart;
    private int filteredTypes;
    private long filteredItems;
    private int seenRevision = Integer.MIN_VALUE;
    private String query = "";
    private int sortMode;
    private TerminalFilter.CategoryMode categoryMode = TerminalFilter.CategoryMode.TYPE;

    public TerminalScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null, null);
    }

    public TerminalScreenHandler(int syncId, PlayerInventory playerInventory,
                                 StorageCoreBlockEntity core) {
        this(syncId, playerInventory, core, null);
    }

    public TerminalScreenHandler(int syncId, PlayerInventory playerInventory,
                                 StorageCoreBlockEntity core, RemoteAccessHandle remoteAccessHandle) {
        super(TriStorageMod.TERMINAL_SCREEN_HANDLER, syncId);
        this.core = core;
        this.remoteAccessHandle = remoteAccessHandle;
        this.remote = remoteAccessHandle != null;
        if (playerInventory.player instanceof ServerPlayerEntity serverPlayer) {
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
        for (int index = 0; index < CATEGORY_WINDOW; index++) {
            addSlot(new DisplaySlot(display, CATEGORY_START + index,
                    6 + index * 24, -21));
        }
        addPlayerInventory(playerInventory, 8, 140);
        addProperties(syncedProperties);
        refreshPage(true);
    }

    @Override
    public void sendContentUpdates() {
        if (core != null && seenRevision != core.revision()) {
            refreshPage(true);
        }
        super.sendContentUpdates();
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (core == null) {
            return false;
        }
        switch (id) {
            case 0 -> setPage(page - 1);
            case 1 -> setPage(page + 1);
            case 2 -> depositPlayerInventory();
            case 3 -> {
                sortMode = (sortMode + 1) % 3;
                page = 0;
                refreshPage(false);
            }
            case 4 -> setCategory(categoryIndex - 1);
            case 5 -> setCategory(categoryIndex + 1);
            case 6 -> setQuery("");
            case 701, 702 -> {
                categoryMode = TerminalFilter.CategoryMode.byNetworkId(id - 700);
                categoryIndex = 0;
                page = 0;
                refreshPage(true);
            }
            case 703 -> {
                categoryIndex = 0;
                page = 0;
                refreshPage(false);
            }
            case 100 -> {
                if (!query.isEmpty()) {
                    setQuery(query.substring(0, query.offsetByCodePoints(query.length(), -1)));
                }
            }
            default -> {
                if (id >= 1000 && id <= 1000 + Character.MAX_CODE_POINT
                        && query.codePointCount(0, query.length()) < TerminalFilter.MAX_QUERY_LENGTH) {
                    int codePoint = id - 1000;
                    if (!Character.isISOControl(codePoint)) {
                        setQuery(query + Character.toString(codePoint));
                    }
                } else {
                    return false;
                }
            }
        }
        sendContentUpdates();
        return true;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (core != null && slotIndex >= CATEGORY_START && slotIndex < PLAYER_START) {
            int selected = categoryWindowStart + slotIndex - CATEGORY_START;
            if (selected < categories.size()) {
                setCategory(selected);
                sendContentUpdates();
            }
            return;
        }
        if (core != null && actionType == SlotActionType.QUICK_MOVE
                && slotIndex >= 0 && slotIndex < slots.size()) {
            quickMove(player, slotIndex);
            return;
        }
        if (core != null && actionType == SlotActionType.PICKUP_ALL) {
            core.batchMutations(() -> {
                if (slotIndex >= 0 && slotIndex < PAGE_SIZE) {
                    withdrawAllMatching(slotIndex, player);
                } else if (slotIndex >= PLAYER_START && slotIndex < slots.size()) {
                    depositAllMatching(player, slots.get(slotIndex).getStack());
                }
            });
            refreshPage(false);
            return;
        }
        if (core == null || slotIndex < 0 || slotIndex >= PAGE_SIZE) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }
        if (actionType == SlotActionType.PICKUP) {
            ItemStack cursor = getCursorStack();
            if (cursor.isEmpty()) {
                ItemStack extracted = extractSlot(slotIndex, button == 1 ? 1 : 64);
                if (!extracted.isEmpty()) setCursorStack(extracted);
            } else {
                long inserted = core.insert(cursor, button == 1 ? 1L : cursor.getCount());
                cursor.decrement((int) inserted);
                setCursorStack(cursor);
            }
            refreshPage(false);
        } else if (actionType == SlotActionType.THROW) {
            ItemStack extracted = extractSlot(slotIndex, button == 0 ? 1 : 64);
            if (!extracted.isEmpty()) player.dropItem(extracted, true);
            refreshPage(false);
        } else if (actionType == SlotActionType.CLONE && player.isInCreativeMode()) {
            ItemStack shown = slots.get(slotIndex).getStack().copy();
            if (!shown.isEmpty()) {
                shown.setCount(shown.getMaxCount());
                setCursorStack(shown);
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (core == null || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        int revisionBefore = core.revision();
        ItemStack result = core.batchMutations(() -> quickMoveUnbatched(player, slotIndex));
        if (core.revision() != revisionBefore) refreshPage(false);
        return result;
    }

    private ItemStack quickMoveUnbatched(PlayerEntity player, int slotIndex) {
        if (slotIndex < PAGE_SIZE) {
            ItemStack extracted = extractSlot(slotIndex, 64);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack original = extracted.copy();
            if (!insertItem(extracted, PLAYER_START, slots.size(), true)) {
                core.insert(original, original.getCount());
                return ItemStack.EMPTY;
            }
            if (!extracted.isEmpty()) {
                core.insert(extracted, extracted.getCount());
            }
            return original;
        }
        if (slotIndex < PLAYER_START) {
            return ItemStack.EMPTY;
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

    @Override
    public boolean canUse(PlayerEntity player) {
        if (core == null || core.isRemoved()) {
            return core == null;
        }
        if (remote) {
            return remoteAccessHandle.isValid();
        }
        return player.squaredDistanceTo(
                core.getPos().getX() + 0.5,
                core.getPos().getY() + 0.5,
                core.getPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (remoteAccessHandle != null) {
            remoteAccessHandle.close();
        }
    }

    public int page() { return syncedProperties.get(PAGE_SIZE); }
    public int pageCount() { return Math.max(1, syncedProperties.get(PAGE_SIZE + 1)); }
    public int storedTypes() { return syncedProperties.get(PAGE_SIZE + 2); }
    public int totalItems() { return syncedProperties.get(PAGE_SIZE + 3); }
    public int categoryIndex() { return syncedProperties.get(PAGE_SIZE + 4); }
    public int categoryCount() { return Math.max(1, syncedProperties.get(PAGE_SIZE + 5)); }
    public int categoryWindowStart() { return syncedProperties.get(PAGE_SIZE + 6); }
    public TerminalFilter.CategoryMode categoryMode() {
        return TerminalFilter.CategoryMode.byNetworkId(syncedProperties.get(PAGE_SIZE + 7));
    }
    public int sortMode() { return syncedProperties.get(PAGE_SIZE + 8); }

    public void applyClientFilter(String nextQuery, TerminalFilter.CategoryMode requestedMode) {
        if (core == null) return;
        String selected = categories.isEmpty() ? TerminalFilter.ALL
                : categories.get(Math.min(categoryIndex, categories.size() - 1)).id();
        TerminalFilter.Selection safe = TerminalFilter.sanitize(
                nextQuery, requestedMode, selected);
        query = safe.query();
        categoryMode = safe.mode();
        page = 0;
        refreshPage(false);
        sendContentUpdates();
    }

    public long displayCount(int slot) {
        return slot >= 0 && slot < PAGE_SIZE
                ? Integer.toUnsignedLong(syncedProperties.get(slot)) : 0;
    }

    private void setPage(int requested) {
        page = Math.max(0, Math.min(filteredPageCount() - 1, requested));
        refreshPage(false);
    }

    private void setCategory(int requested) {
        categoryIndex = Math.max(0, Math.min(categories.size() - 1, requested));
        page = 0;
        refreshPage(false);
    }

    private void setQuery(String next) {
        query = TerminalFilter.normalize(next);
        page = 0;
        refreshPage(false);
    }

    private ItemStack extractSlot(int slotIndex, int requested) {
        String key = keys.get(slotIndex);
        return key.isEmpty() ? ItemStack.EMPTY : core.extract(key, requested);
    }

    private void depositPlayerInventory() {
        core.batchMutations(() -> {
            for (int index = PLAYER_START; index < slots.size(); index++) {
                Slot slot = slots.get(index);
                if (!slot.hasStack()) continue;
                ItemStack source = slot.getStack();
                long inserted = core.insert(source, source.getCount());
                if (inserted > 0) {
                    source.decrement((int) inserted);
                    slot.markDirty();
                }
            }
        });
        refreshPage(false);
    }

    private void refreshPage(boolean rebuildCategories) {
        if (core == null) {
            return;
        }
        String selectedId = categories.isEmpty() ? TerminalFilter.ALL
                : categories.get(Math.min(categoryIndex, categories.size() - 1)).id();
        StorageCoreBlockEntity.EntryOrder order = switch (sortMode) {
            case 1 -> StorageCoreBlockEntity.EntryOrder.COUNT;
            case 2 -> StorageCoreBlockEntity.EntryOrder.RECENT;
            default -> StorageCoreBlockEntity.EntryOrder.REGISTRY;
        };
        StorageCoreBlockEntity.BrowseResult result = core.browse(page, PAGE_SIZE, order,
                TerminalFilter.sanitize(query, categoryMode, selectedId));
        page = result.page();
        filteredTypes = result.filteredTypes();
        filteredItems = result.filteredItems();
        categories = result.categories();
        categoryIndex = 0;
        for (int index = 0; index < categories.size(); index++) {
            if (categories.get(index).id().equals(result.selectedCategory())) {
                categoryIndex = index;
                break;
            }
        }
        List<StorageCoreBlockEntity.EntryView> views = result.entries();
        for (int index = 0; index < PAGE_SIZE; index++) {
            if (index < views.size()) {
                StorageCoreBlockEntity.EntryView view = views.get(index);
                display.setStack(index, view.stack().copyWithCount(1));
                keys.set(index, view.key());
                counts[index] = view.count();
            } else {
                display.setStack(index, ItemStack.EMPTY);
                keys.set(index, "");
                counts[index] = 0L;
            }
        }
        categoryWindowStart = Math.max(0,
                Math.min(categoryIndex - CATEGORY_WINDOW / 2, categories.size() - CATEGORY_WINDOW));
        for (int index = 0; index < CATEGORY_WINDOW; index++) {
            int sourceIndex = categoryWindowStart + index;
            display.setStack(CATEGORY_START + index,
                    sourceIndex < categories.size() ? categories.get(sourceIndex).icon().copy() : ItemStack.EMPTY);
        }
        seenRevision = core.revision();
    }

    private int filteredPageCount() {
        return Math.max(1, (filteredTypes + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void withdrawAllMatching(int slotIndex, PlayerEntity player) {
        String key = keys.get(slotIndex);
        ItemStack shown = slots.get(slotIndex).getStack();
        if (key.isEmpty() || shown.isEmpty()) return;

        ItemStack cursor = getCursorStack();
        if (!cursor.isEmpty()) {
            if (!ItemStack.areItemsAndComponentsEqual(cursor, shown)) return;
            player.getInventory().insertStack(cursor);
            setCursorStack(cursor);
            if (!cursor.isEmpty()) return;
        }
        while (true) {
            ItemStack extracted = core.extract(key, shown.getMaxCount());
            if (extracted.isEmpty()) break;
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
        ItemStack reference = (cursor.isEmpty() ? clickedStack : cursor).copyWithCount(1);
        if (reference.isEmpty()) return;
        if (!cursor.isEmpty() && ItemStack.areItemsAndComponentsEqual(cursor, reference)) {
            long inserted = core.insert(cursor, cursor.getCount());
            cursor.decrement((int) inserted);
            setCursorStack(cursor);
        }
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = player.getInventory().getStack(inventorySlot);
            if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, reference)) {
                long inserted = core.insert(stack, stack.getCount());
                stack.decrement((int) inserted);
            }
        }
        player.getInventory().markDirty();
    }

    private PropertyDelegate properties(StorageCoreBlockEntity core) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                if (index < PAGE_SIZE) {
                    return (int) counts[index];
                }
                return switch (index - PAGE_SIZE) {
                    case 0 -> page;
                    case 1 -> filteredPageCount();
                    case 2 -> filteredTypes;
                    case 3 -> (int) Math.min(Integer.MAX_VALUE, filteredItems);
                    case 4 -> categoryIndex;
                    case 5 -> categories.size();
                    case 6 -> categoryWindowStart;
                    case 7 -> categoryMode.ordinal();
                    case 8 -> sortMode;
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
        public boolean canInsert(ItemStack stack) { return false; }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) { return false; }
    }
}
