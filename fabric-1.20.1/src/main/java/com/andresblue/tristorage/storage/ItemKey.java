package com.andresblue.tristorage.storage;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Structural item identity used by storage lookups. Its equality deliberately
 * mirrors ItemStack.canCombine: item identity plus equal NBT, ignoring count.
 */
public final class ItemKey {
    private final Item item;
    private final CompoundTag nbt;
    private final int hash;

    private ItemKey(Item item, CompoundTag nbt, boolean copyNbt) {
        this.item = item;
        this.nbt = nbt == null ? null : (copyNbt ? nbt.copy() : nbt);
        this.hash = 31 * System.identityHashCode(item) + Objects.hashCode(this.nbt);
    }

    /** Immutable key suitable for retention by StorageRuntime. */
    public static ItemKey frozen(ItemStack stack) {
        return new ItemKey(stack.getItem(), stack.getTag(), true);
    }

    /** Short-lived lookup key. It must never be retained after the lookup. */
    public static ItemKey probe(ItemStack stack) {
        return new ItemKey(stack.getItem(), stack.getTag(), false);
    }

    public Item item() {
        return item;
    }

    public CompoundTag copyNbt() {
        return nbt == null ? null : nbt.copy();
    }

    public ItemStack toStack() {
        ItemStack stack = new ItemStack(item);
        if (nbt != null) {
            stack.setTag(nbt.copy());
        }
        return stack;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ItemKey key
                && item == key.item && Objects.equals(nbt, key.nbt);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
