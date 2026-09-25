package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class CoreScreenHandler extends ScreenHandler {
    private static final int PROPERTY_COUNT = 8;
    private final Inventory input = new SimpleInventory(1);
    private final StorageCoreBlockEntity core;
    private final PropertyDelegate syncedProperties;

    public CoreScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    public CoreScreenHandler(int syncId, PlayerInventory playerInventory, StorageCoreBlockEntity core) {
        super(TriStorageMod.CORE_SCREEN_HANDLER, syncId);
        this.core = core;
        this.syncedProperties = core == null
                ? new ArrayPropertyDelegate(PROPERTY_COUNT)
                : properties(core);
        addSlot(new Slot(input, 0, 80, 35) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(Items.CHEST);
            }
        });
        addPlayerInventory(playerInventory, 8, 122);
        addProperties(syncedProperties);
    }

    @Override
    public void sendContentUpdates() {
        absorbInput();
        super.sendContentUpdates();
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id != 0 || core == null) {
            return false;
        }
        int removed = core.removeChests(64);
        if (removed > 0) {
            player.getInventory().offerOrDrop(new ItemStack(Items.CHEST, removed));
            return true;
        }
        return false;
    }

    @Override
    public ItemStack transferSlot(PlayerEntity player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        if (slotIndex == 0) {
            if (!insertItem(source, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (source.isOf(Items.CHEST)) {
            if (!insertItem(source, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return original;
    }

    @Override
    public void close(PlayerEntity player) {
        absorbInput();
        super.close(player);
        dropInventory(player, input);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return core == null || (!core.isRemoved()
                && player.squaredDistanceTo(
                core.getPos().getX() + 0.5,
                core.getPos().getY() + 0.5,
                core.getPos().getZ() + 0.5) <= 64.0);
    }

    public int installedChests() {
        return syncedProperties.get(0);
    }

    public int maxChests() {
        return syncedProperties.get(1);
    }

    public int storedTypes() {
        return syncedProperties.get(2);
    }

    public int typeCapacity() {
        return syncedProperties.get(3);
    }

    public long totalItems() {
        return Integer.toUnsignedLong(syncedProperties.get(4))
                | (Integer.toUnsignedLong(syncedProperties.get(5)) << 32);
    }

    public long itemCapacity() {
        return Integer.toUnsignedLong(syncedProperties.get(6))
                | (Integer.toUnsignedLong(syncedProperties.get(7)) << 32);
    }

    private void absorbInput() {
        if (core == null) {
            return;
        }
        ItemStack stack = input.getStack(0);
        if (!stack.isOf(Items.CHEST)) {
            return;
        }
        int accepted = core.addChests(stack.getCount());
        if (accepted > 0) {
            stack.decrement(accepted);
            input.markDirty();
        }
    }

    private static PropertyDelegate properties(StorageCoreBlockEntity core) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                long total = core.totalItems();
                long capacity = core.itemCapacity();
                return switch (index) {
                    case 0 -> core.installedChests();
                    case 1 -> core.maxChests();
                    case 2 -> core.storedTypes();
                    case 3 -> core.typeCapacity();
                    case 4 -> (int) total;
                    case 5 -> (int) (total >>> 32);
                    case 6 -> (int) capacity;
                    case 7 -> (int) (capacity >>> 32);
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
}
