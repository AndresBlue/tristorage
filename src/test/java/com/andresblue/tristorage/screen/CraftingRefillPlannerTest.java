package com.andresblue.tristorage.screen;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingRefillPlannerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void refillsOnlyAnIngredientSlotLeftEmptyByVanilla() {
        ItemStack plank = new ItemStack(Items.OAK_PLANKS);

        assertTrue(CraftingRefillPlanner.shouldRefill(plank, ItemStack.EMPTY));
        assertFalse(CraftingRefillPlanner.shouldRefill(
                plank, new ItemStack(Items.BUCKET)));
        assertFalse(CraftingRefillPlanner.shouldRefill(
                plank, new ItemStack(Items.OAK_PLANKS)));
        assertFalse(CraftingRefillPlanner.shouldRefill(
                ItemStack.EMPTY, ItemStack.EMPTY));
    }

    @Test
    void quickMoveIsLimitedToOneOutputStack() {
        assertEquals(64, CraftingRefillPlanner.quickMoveOutputLimit(
                new ItemStack(Items.CHEST)));
        assertEquals(1, CraftingRefillPlanner.quickMoveOutputLimit(
                new ItemStack(Items.DIAMOND_SWORD)));
        assertEquals(0, CraftingRefillPlanner.quickMoveOutputLimit(ItemStack.EMPTY));
    }
}
