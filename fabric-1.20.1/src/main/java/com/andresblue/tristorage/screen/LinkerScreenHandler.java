package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class LinkerScreenHandler extends ScreenHandler {
    private static final int ANTENNA_SLOT = 0;
    private static final int PLAYER_START = 1;
    private static final int PLAYER_END = 37;
    private final Inventory inventory;
    private final LinkerBlockEntity linker;
    private final PropertyDelegate properties;

    public LinkerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    public LinkerScreenHandler(int syncId, PlayerInventory playerInventory,
                               LinkerBlockEntity linker) {
        super(TriStorageMod.LINKER_SCREEN_HANDLER, syncId);
        this.linker = linker;
        this.inventory = linker == null ? new SimpleInventory(1) : linker;
        this.properties = linker == null ? new ArrayPropertyDelegate(3) : properties(linker);
        inventory.onOpen(playerInventory.player);

        addSlot(new Slot(inventory, ANTENNA_SLOT, 80, 23) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(TriStorageMod.DIMENSIONAL_ANTENNA)
                        && (LinkerScreenHandler.this.linker == null
                        || LinkerScreenHandler.this.linker.canInstallAntenna());
            }

            @Override
            public int getMaxItemCount() {
                return 1;
            }
        });
        addPlayerInventory(playerInventory, 8, 84);
        addProperties(properties);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(slotIndex);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        if (slotIndex == ANTENNA_SLOT) {
            if (!insertItem(source, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!insertItem(source, ANTENNA_SLOT, ANTENNA_SLOT + 1, false)) {
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
    public boolean canUse(PlayerEntity player) {
        return linker == null || linker.canPlayerUse(player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        inventory.onClose(player);
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

    private static PropertyDelegate properties(LinkerBlockEntity linker) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> linker.hasAntenna() ? 1 : 0;
                    case 1 -> linker.isAntennaActive() ? 1 : 0;
                    case 2 -> linker.canInstallAntenna() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int size() {
                return 3;
            }
        };
    }

    private void addPlayerInventory(PlayerInventory playerInventory, int x, int y) {
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
