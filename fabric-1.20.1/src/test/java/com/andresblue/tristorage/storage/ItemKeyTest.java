package com.andresblue.tristorage.storage;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ItemKeyTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void equalityMatchesCanCombineIdentityAndNbt() {
        ItemStack first = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag tag = new CompoundTag();
        tag.putString("Custom", "alpha");
        first.setTag(tag);
        ItemStack same = first.copy();
        ItemStack different = first.copy();
        different.getOrCreateTag().putString("Custom", "beta");

        assertEquals(ItemStack.isSameItemSameTags(first, same),
                ItemKey.frozen(first).equals(ItemKey.probe(same)));
        assertEquals(ItemStack.isSameItemSameTags(first, different),
                ItemKey.frozen(first).equals(ItemKey.probe(different)));
        assertNotEquals(ItemKey.frozen(first), ItemKey.frozen(new ItemStack(Items.STONE)));
    }

    @Test
    void frozenKeyDoesNotObserveLaterStackMutation() {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.getOrCreateTag().putInt("Payload", 1);
        ItemKey frozen = ItemKey.frozen(stack);
        stack.getOrCreateTag().putInt("Payload", 2);

        ItemStack original = new ItemStack(Items.SHULKER_BOX);
        original.getOrCreateTag().putInt("Payload", 1);
        assertEquals(frozen, ItemKey.probe(original));
    }
}
