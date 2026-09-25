package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class LinkerScreenHandler extends AbstractContainerMenu {
    private static final int ANTENNA_SLOT = 0;
    private static final int PLAYER_START = 1;
    private static final int PLAYER_END = 37;
    private final Container inventory;
    private final LinkerBlockEntity linker;
    private final ContainerData properties;

    public LinkerScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    public LinkerScreenHandler(int syncId, Inventory playerInventory,
                               LinkerBlockEntity linker) {
        super(TriStorageMod.LINKER_SCREEN_HANDLER.get(), syncId);
        this.linker = linker;
        this.inventory = linker == null ? new SimpleContainer(1) : linker;
        this.properties = linker == null ? new SimpleContainerData(3) : properties(linker);
        inventory.startOpen(playerInventory.player);

        addSlot(new Slot(inventory, ANTENNA_SLOT, 80, 23) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(TriStorageMod.DIMENSIONAL_ANTENNA.get())
                        && (LinkerScreenHandler.this.linker == null
                        || LinkerScreenHandler.this.linker.canInstallAntenna());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addPlayerInventory(playerInventory, 8, 84);
        addDataSlots(properties);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (slotIndex == ANTENNA_SLOT) {
            if (!moveItemStackTo(source, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(source, ANTENNA_SLOT, ANTENNA_SLOT + 1, false)) {
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
    public boolean stillValid(Player player) {
        return linker == null || linker.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        inventory.stopOpen(player);
    }

    public boolean hasAntenna() {
        return properties.get(0) != 0;
    }

    public boolean antennaActive() {
        return properties.get(1) != 0;
    }

    public boolean installationSpaceAvailable() {
        return properties.get(2) != 0;
    }

    private static ContainerData properties(LinkerBlockEntity linker) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> linker.hasDimensionalAntenna() ? 1 : 0;
                    case 1 -> linker.isAntennaActive() ? 1 : 0;
                    case 2 -> linker.canInstallAntenna() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    private void addPlayerInventory(Inventory playerInventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        x + column * 18, y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, x + column * 18, y + 58));
        }
    }
}
