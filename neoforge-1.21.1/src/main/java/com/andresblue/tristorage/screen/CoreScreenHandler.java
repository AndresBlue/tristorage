package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CoreScreenHandler extends AbstractContainerMenu {
    private static final int PROPERTY_COUNT = 8;
    private final Container input = new SimpleContainer(1);
    private final StorageCoreBlockEntity core;
    private final ContainerData syncedProperties;

    public CoreScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    public CoreScreenHandler(int syncId, Inventory playerInventory, StorageCoreBlockEntity core) {
        super(TriStorageMod.CORE_SCREEN_HANDLER.get(), syncId);
        this.core = core;
        this.syncedProperties = core == null
                ? new SimpleContainerData(PROPERTY_COUNT)
                : properties(core);
        addSlot(new Slot(input, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.CHEST);
            }
        });
        addPlayerInventory(playerInventory, 8, 122);
        addDataSlots(syncedProperties);
    }

    @Override
    public void broadcastChanges() {
        absorbInput();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || core == null) {
            return false;
        }
        int removed = core.removeChests(64);
        if (removed > 0) {
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.CHEST, removed));
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (slotIndex == 0) {
            if (!moveItemStackTo(source, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (source.is(Items.CHEST)) {
            if (!moveItemStackTo(source, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        absorbInput();
        super.removed(player);
        clearContainer(player, input);
    }

    @Override
    public boolean stillValid(Player player) {
        return core == null || (!core.isRemoved()
                && player.distanceToSqr(
                core.getBlockPos().getX() + 0.5,
                core.getBlockPos().getY() + 0.5,
                core.getBlockPos().getZ() + 0.5) <= 64.0);
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
        ItemStack stack = input.getItem(0);
        if (!stack.is(Items.CHEST)) {
            return;
        }
        int accepted = core.addChests(stack.getCount());
        if (accepted > 0) {
            stack.shrink(accepted);
            input.setChanged();
        }
    }

    private static ContainerData properties(StorageCoreBlockEntity core) {
        return new ContainerData() {
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
}
