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
    private static final int INSTALLED_CHESTS = 0;
    private static final int MAX_CHESTS = INSTALLED_CHESTS + PropertyWords.INT_WORDS;
    private static final int STORED_TYPES = MAX_CHESTS + PropertyWords.INT_WORDS;
    private static final int TYPE_CAPACITY = STORED_TYPES + PropertyWords.INT_WORDS;
    private static final int TOTAL_ITEMS = TYPE_CAPACITY + PropertyWords.INT_WORDS;
    private static final int ITEM_CAPACITY = TOTAL_ITEMS + PropertyWords.LONG_WORDS;
    private static final int PROPERTY_COUNT = ITEM_CAPACITY + PropertyWords.LONG_WORDS;
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
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
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
    public void onClosed(PlayerEntity player) {
        absorbInput();
        super.onClosed(player);
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
        return (int) PropertyWords.read(
                syncedProperties, INSTALLED_CHESTS, PropertyWords.INT_WORDS);
    }

    public int maxChests() {
        return (int) PropertyWords.read(syncedProperties, MAX_CHESTS, PropertyWords.INT_WORDS);
    }

    public int storedTypes() {
        return (int) PropertyWords.read(syncedProperties, STORED_TYPES, PropertyWords.INT_WORDS);
    }

    public int typeCapacity() {
        return (int) PropertyWords.read(
                syncedProperties, TYPE_CAPACITY, PropertyWords.INT_WORDS);
    }

    public long totalItems() {
        return PropertyWords.read(syncedProperties, TOTAL_ITEMS, PropertyWords.LONG_WORDS);
    }

    public long itemCapacity() {
        return PropertyWords.read(syncedProperties, ITEM_CAPACITY, PropertyWords.LONG_WORDS);
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
                if (index < MAX_CHESTS) {
                    return PropertyWords.word(core.installedChests(), index - INSTALLED_CHESTS);
                }
                if (index < STORED_TYPES) {
                    return PropertyWords.word(core.maxChests(), index - MAX_CHESTS);
                }
                if (index < TYPE_CAPACITY) {
                    return PropertyWords.word(core.storedTypes(), index - STORED_TYPES);
                }
                if (index < TOTAL_ITEMS) {
                    return PropertyWords.word(core.typeCapacity(), index - TYPE_CAPACITY);
                }
                if (index < ITEM_CAPACITY) {
                    return PropertyWords.word(core.totalItems(), index - TOTAL_ITEMS);
                }
                if (index < PROPERTY_COUNT) {
                    return PropertyWords.word(core.itemCapacity(), index - ITEM_CAPACITY);
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
}
