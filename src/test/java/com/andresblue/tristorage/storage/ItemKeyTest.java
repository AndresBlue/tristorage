package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.TestStacks;
import com.andresblue.tristorage.version.Stacks;
import net.minecraft.SharedConstants;
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
        TestStacks.edit(first, tag -> tag.putString("Custom", "alpha"));
        ItemStack same = first.copy();
        ItemStack different = first.copy();
        TestStacks.edit(different, tag -> tag.putString("Custom", "beta"));

        assertEquals(Stacks.sameItemSameData(first, same),
                ItemKey.frozen(first).equals(ItemKey.probe(same)));
        assertEquals(Stacks.sameItemSameData(first, different),
                ItemKey.frozen(first).equals(ItemKey.probe(different)));
        assertNotEquals(ItemKey.frozen(first), ItemKey.frozen(new ItemStack(Items.STONE)));
    }

    @Test
    void frozenKeyDoesNotObserveLaterStackMutation() {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        TestStacks.edit(stack, tag -> tag.putInt("Payload", 1));
        ItemKey frozen = ItemKey.frozen(stack);
        TestStacks.edit(stack, tag -> tag.putInt("Payload", 2));

        ItemStack original = new ItemStack(Items.SHULKER_BOX);
        TestStacks.edit(original, tag -> tag.putInt("Payload", 1));
        assertEquals(frozen, ItemKey.probe(original));
    }
}
