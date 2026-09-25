package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.version.Stacks;
import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Structural item identity used by storage lookups. Its equality deliberately
 * mirrors the vanilla "same item, same data" check: item identity plus equal
 * NBT (before 1.20.5) or components (after), ignoring count.
 */
public final class ItemKey {
    private final Item item;
    private final Object data;
    private final int hash;

    private ItemKey(Item item, Object data) {
        this.item = item;
        this.data = data;
        this.hash = 31 * System.identityHashCode(item) + Objects.hashCode(data);
    }

    /** Immutable key suitable for retention by StorageRuntime. */
    public static ItemKey frozen(ItemStack stack) {
        return new ItemKey(stack.getItem(), Stacks.dataIdentity(stack, true));
    }

    /** Short-lived lookup key. It must never be retained after the lookup. */
    public static ItemKey probe(ItemStack stack) {
        return new ItemKey(stack.getItem(), Stacks.dataIdentity(stack, false));
    }

    public Item item() {
        return item;
    }

    public ItemStack toStack() {
        return Stacks.withData(item, data);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ItemKey key
                && item == key.item && Objects.equals(data, key.data);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
