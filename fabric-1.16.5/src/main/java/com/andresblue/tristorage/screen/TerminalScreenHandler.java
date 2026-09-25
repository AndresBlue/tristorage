package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.RemoteChunkLease;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TerminalScreenHandler extends ScreenHandler {
    public static final int PAGE_SIZE = 54;
    private static final int PROPERTY_COUNT = PAGE_SIZE + 4;
    private static final int PLAYER_START = PAGE_SIZE;

    private final SimpleInventory display = new SimpleInventory(PAGE_SIZE);
    private final PlayerInventory playerInventory;
    private final StorageCoreBlockEntity core;
    private final boolean remote;
    private final RemoteChunkLease remoteChunkLease;
    private final PropertyDelegate syncedProperties;
    private final List<String> keys = new ArrayList<>(Collections.nCopies(PAGE_SIZE, ""));
    private final long[] counts = new long[PAGE_SIZE];
    private int page;
    private int seenRevision = Integer.MIN_VALUE;

    public TerminalScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null, null);
    }

    public TerminalScreenHandler(int syncId, PlayerInventory playerInventory,
                                 StorageCoreBlockEntity core) {
        this(syncId, playerInventory, core, null);
    }

    public TerminalScreenHandler(int syncId, PlayerInventory playerInventory,
                                 StorageCoreBlockEntity core, RemoteChunkLease remoteChunkLease) {
        super(TriStorageMod.TERMINAL_SCREEN_HANDLER, syncId);
        this.playerInventory = playerInventory;
        this.core = core;
        this.remoteChunkLease = remoteChunkLease;
        this.remote = remoteChunkLease != null;
        this.syncedProperties = core == null
                ? new ArrayPropertyDelegate(PROPERTY_COUNT)
                : properties(core);
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new DisplaySlot(display, column + row * 9,
                        8 + column * 18, 18 + row * 18));
            }
        }
        addPlayerInventory(playerInventory, 8, 174);
        addProperties(syncedProperties);
        refreshPage();
    }

    @Override
    public void sendContentUpdates() {
        if (core != null && seenRevision != core.revision()) {
            refreshPage();
        }
        super.sendContentUpdates();
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (core == null) {
            return false;
        }
        int nextPage;
        switch (id) {
            case 0:
                nextPage = Math.max(0, page - 1);
                break;
            case 1:
                nextPage = Math.min(core.pageCount(PAGE_SIZE) - 1, page + 1);
                break;
            default:
                nextPage = page;
        }
        if (nextPage != page) {
            page = nextPage;
            refreshPage();
            sendContentUpdates();
        }
        return id == 0 || id == 1;
    }

    @Override
    public ItemStack onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (core != null && slotIndex >= 0 && slotIndex < PAGE_SIZE
                && actionType == SlotActionType.PICKUP) {
            ItemStack cursor = playerInventory.getCursorStack();
            if (cursor.isEmpty()) {
                int requested = button == 1 ? 1 : 64;
                ItemStack extracted = extractSlot(slotIndex, requested);
                if (!extracted.isEmpty()) {
                    playerInventory.setCursorStack(extracted);
                }
            } else {
                long requested = button == 1 ? 1 : cursor.getCount();
                long inserted = core.insert(cursor, requested);
                cursor.decrement((int) inserted);
                playerInventory.setCursorStack(cursor);
            }
            refreshPage();
            return ItemStack.EMPTY;
        }
        return super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack transferSlot(PlayerEntity player, int slotIndex) {
        if (core == null || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        if (slotIndex < PAGE_SIZE) {
            ItemStack extracted = extractSlot(slotIndex, 64);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack original = extracted.copy();
            if (!insertItem(extracted, PLAYER_START, slots.size(), true)) {
                core.insert(original, original.getCount());
                refreshPage();
                return ItemStack.EMPTY;
            }
            if (!extracted.isEmpty()) {
                core.insert(extracted, extracted.getCount());
            }
            refreshPage();
            return original;
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
        refreshPage();
        return original;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (core == null || core.isRemoved()) {
            return core == null;
        }
        return remote || player.squaredDistanceTo(
                core.getPos().getX() + 0.5,
                core.getPos().getY() + 0.5,
                core.getPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        if (remoteChunkLease != null) {
            remoteChunkLease.release();
        }
    }

    public int page() {
        return syncedProperties.get(PAGE_SIZE);
    }

    public int pageCount() {
        return Math.max(1, syncedProperties.get(PAGE_SIZE + 1));
    }

    public int storedTypes() {
        return syncedProperties.get(PAGE_SIZE + 2);
    }

    public int totalItems() {
        return syncedProperties.get(PAGE_SIZE + 3);
    }

    public long displayCount(int slot) {
        return slot >= 0 && slot < PAGE_SIZE
                ? Integer.toUnsignedLong(syncedProperties.get(slot))
                : 0;
    }

    private ItemStack extractSlot(int slotIndex, int requested) {
        String key = keys.get(slotIndex);
        return key.isEmpty() ? ItemStack.EMPTY : core.extract(key, requested);
    }

    private void refreshPage() {
        if (core == null) {
            return;
        }
        page = Math.min(page, core.pageCount(PAGE_SIZE) - 1);
        List<StorageCoreBlockEntity.EntryView> views = core.page(page, PAGE_SIZE);
        for (int index = 0; index < PAGE_SIZE; index++) {
            if (index < views.size()) {
                StorageCoreBlockEntity.EntryView view = views.get(index);
                ItemStack shown = view.stack().copy();
                shown.setCount(1);
                display.setStack(index, shown);
                keys.set(index, view.key());
                counts[index] = view.count();
            } else {
                display.setStack(index, ItemStack.EMPTY);
                keys.set(index, "");
                counts[index] = 0;
            }
        }
        seenRevision = core.revision();
    }

    private PropertyDelegate properties(StorageCoreBlockEntity core) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                if (index < PAGE_SIZE) {
                    return (int) counts[index];
                }
                switch (index - PAGE_SIZE) {
                    case 0: return page;
                    case 1: return core.pageCount(PAGE_SIZE);
                    case 2: return core.storedTypes();
                    case 3: return (int) Math.min(Integer.MAX_VALUE, core.totalItems());
                    default: return 0;
                }
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
            return false;
        }
    }
}
