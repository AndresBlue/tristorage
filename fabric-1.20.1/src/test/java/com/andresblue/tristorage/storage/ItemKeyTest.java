package com.andresblue.tristorage.storage;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ItemKeyTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void equalityMatchesCanCombineIdentityAndNbt() {
        ItemStack first = new ItemStack(Items.DIAMOND_SWORD);
        NbtCompound tag = new NbtCompound();
        tag.putString("Custom", "alpha");
        first.setNbt(tag);
        ItemStack same = first.copy();
        ItemStack different = first.copy();
        different.getOrCreateNbt().putString("Custom", "beta");

        assertEquals(ItemStack.canCombine(first, same),
                ItemKey.frozen(first).equals(ItemKey.probe(same)));
        assertEquals(ItemStack.canCombine(first, different),
                ItemKey.frozen(first).equals(ItemKey.probe(different)));
        assertNotEquals(ItemKey.frozen(first), ItemKey.frozen(new ItemStack(Items.STONE)));
    }

    @Test
    void frozenKeyDoesNotObserveLaterStackMutation() {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.getOrCreateNbt().putInt("Payload", 1);
        ItemKey frozen = ItemKey.frozen(stack);
        stack.getOrCreateNbt().putInt("Payload", 2);

        ItemStack original = new ItemStack(Items.SHULKER_BOX);
        original.getOrCreateNbt().putInt("Payload", 1);
        assertEquals(frozen, ItemKey.probe(original));
    }
}
