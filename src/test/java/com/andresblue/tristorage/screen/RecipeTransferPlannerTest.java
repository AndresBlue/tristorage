package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TestStacks;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeTransferPlannerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void resolvesIngredientsFromTheCompleteResourceSet() {
        ShapedRecipe recipe = shaped(1, 1,
                Ingredient.of(Items.DRAGON_HEAD));
        RecipeTransferPlanner.Plan plan = RecipeTransferPlanner.plan(recipe,
                List.of(
                        new RecipeTransferPlanner.Resource(
                                new ItemStack(Items.COBBLESTONE), 64),
                        new RecipeTransferPlanner.Resource(
                                new ItemStack(Items.DRAGON_HEAD), 1)),
                1);

        assertNotNull(plan);
        assertTrue(plan.grid().get(0).is(Items.DRAGON_HEAD));
    }

    @Test
    void backtracksAcrossOverlappingIngredientAlternatives() {
        ShapedRecipe recipe = shaped(2, 1,
                Ingredient.of(Items.STONE, Items.DIRT),
                Ingredient.of(Items.DIRT));
        RecipeTransferPlanner.Plan plan = RecipeTransferPlanner.plan(recipe,
                List.of(
                        new RecipeTransferPlanner.Resource(new ItemStack(Items.STONE), 1),
                        new RecipeTransferPlanner.Resource(new ItemStack(Items.DIRT), 1)),
                1);

        assertNotNull(plan);
        assertTrue(plan.grid().get(0).is(Items.STONE));
        assertTrue(plan.grid().get(1).is(Items.DIRT));
    }

    @Test
    void maxTransferRespectsRepeatedIngredientsAndSlotLimits() {
        ShapedRecipe recipe = shaped(3, 1,
                Ingredient.of(Items.STONE),
                Ingredient.of(Items.STONE),
                Ingredient.of(Items.STONE));
        RecipeTransferPlanner.Plan plan = RecipeTransferPlanner.plan(recipe,
                List.of(new RecipeTransferPlanner.Resource(
                        new ItemStack(Items.STONE), 100)),
                RecipeTransferPlanner.MAX_TRANSFER);

        assertNotNull(plan);
        assertEquals(33, plan.crafts());
        assertEquals(99, plan.consumption().values().stream()
                .mapToInt(Integer::intValue).sum());
    }

    @Test
    void availabilityIdentifiesTheExactMissingIngredient() {
        ShapedRecipe recipe = shaped(3, 1,
                Ingredient.of(Items.DIAMOND_BLOCK),
                Ingredient.of(Items.ENDER_PEARL),
                Ingredient.of(Items.DIAMOND_BLOCK));
        RecipeTransferPlanner.Availability availability =
                RecipeTransferPlanner.availability(recipe,
                        List.of(resource(Items.DIAMOND_BLOCK, 2)));

        assertFalse(availability.canCraft());
        assertTrue(availability.isAvailable(0));
        assertFalse(availability.isAvailable(1));
        assertTrue(availability.isAvailable(2));
    }

    @Test
    void availabilityBacktracksBeforeMarkingAlternativeIngredientsMissing() {
        ShapedRecipe recipe = shaped(2, 1,
                Ingredient.of(Items.STONE, Items.DIRT),
                Ingredient.of(Items.DIRT));
        RecipeTransferPlanner.Availability availability =
                RecipeTransferPlanner.availability(recipe,
                        List.of(resource(Items.STONE, 1), resource(Items.DIRT, 1)));

        assertTrue(availability.canCraft());
        assertTrue(availability.isAvailable(0));
        assertTrue(availability.isAvailable(1));
    }

    @Test
    void capacitatedMatchingPreservesTheOnlyNarrowIngredient() {
        ShapedRecipe recipe = shaped(3, 1,
                Ingredient.of(Items.STONE, Items.DIRT),
                Ingredient.of(Items.STONE, Items.DIRT),
                Ingredient.of(Items.DIRT));
        List<RecipeTransferPlanner.Resource> resources = List.of(
                resource(Items.STONE, 2), resource(Items.DIRT, 1));

        RecipeTransferPlanner.Plan plan = RecipeTransferPlanner.plan(recipe, resources, 1);
        RecipeTransferPlanner.Availability availability =
                RecipeTransferPlanner.availability(recipe, resources);

        assertNotNull(plan);
        assertTrue(availability.canCraft());
        assertTrue(plan.grid().get(2).is(Items.DIRT));
    }

    @Test
    void availabilityAccountsForRepeatedIngredientCounts() {
        ShapedRecipe recipe = shaped(2, 1,
                Ingredient.of(Items.STONE),
                Ingredient.of(Items.STONE));
        RecipeTransferPlanner.Availability availability =
                RecipeTransferPlanner.availability(recipe,
                        List.of(resource(Items.STONE, 1)));

        assertFalse(availability.canCraft());
        assertEquals(1, Integer.bitCount(availability.availableMask()));
    }

    @Test
    void parsesAndFillsTheRealStorageTerminalRecipe() {
        ShapedRecipe recipe = TestStacks.shapedRecipe("storage_terminal", """
                        {"type":"minecraft:crafting_shaped",
                         "pattern":["GIG","RHR","ICI"],
                         "key":{"G":{"item":"minecraft:glass"},
                                "I":{"item":"minecraft:iron_ingot"},
                                "R":{"item":"minecraft:redstone"},
                                "H":{"item":"minecraft:chest"},
                                "C":{"item":"minecraft:copper_ingot"}},
                         "result":{"item":"minecraft:crafting_table"}}
                        """);
        RecipeTransferPlanner.Plan plan = RecipeTransferPlanner.plan(recipe,
                List.of(
                        resource(Items.GLASS, 64),
                        resource(Items.IRON_INGOT, 64),
                        resource(Items.REDSTONE, 64),
                        resource(Items.CHEST, 64),
                        resource(Items.COPPER_INGOT, 64)),
                1);

        assertNotNull(plan);
        assertEquals(9, plan.consumption().values().stream()
                .mapToInt(Integer::intValue).sum());
    }

    @Test
    void realStorageTerminalAvailabilityAcceptsExactMinimumCounts() {
        ShapedRecipe recipe = TestStacks.shapedRecipe("storage_terminal_minimum", """
                        {"type":"minecraft:crafting_shaped",
                         "pattern":["GIG","RHR","ICI"],
                         "key":{"G":{"item":"minecraft:glass"},
                                "I":{"item":"minecraft:iron_ingot"},
                                "R":{"item":"minecraft:redstone"},
                                "H":{"item":"minecraft:chest"},
                                "C":{"item":"minecraft:copper_ingot"}},
                         "result":{"item":"minecraft:crafting_table"}}
                        """);
        List<RecipeTransferPlanner.Resource> resources = List.of(
                resource(Items.GLASS, 2),
                resource(Items.IRON_INGOT, 3),
                resource(Items.REDSTONE, 2),
                resource(Items.CHEST, 1),
                resource(Items.COPPER_INGOT, 1));

        RecipeTransferPlanner.Availability availability =
                RecipeTransferPlanner.availability(recipe, resources);
        RecipeTransferPlanner.Plan plan = RecipeTransferPlanner.plan(recipe, resources, 1);

        assertTrue(availability.canCraft());
        assertEquals(0x1FF, availability.availableMask());
        assertNotNull(plan);
    }

    @Test
    void realStorageTerminalAvailabilityMarksOnlyTheMissingSlot() {
        ShapedRecipe recipe = TestStacks.shapedRecipe("storage_terminal_missing", """
                        {"type":"minecraft:crafting_shaped",
                         "pattern":["GIG","RHR","ICI"],
                         "key":{"G":{"item":"minecraft:glass"},
                                "I":{"item":"minecraft:iron_ingot"},
                                "R":{"item":"minecraft:redstone"},
                                "H":{"item":"minecraft:chest"},
                                "C":{"item":"minecraft:copper_ingot"}},
                         "result":{"item":"minecraft:crafting_table"}}
                        """);
        RecipeTransferPlanner.Availability availability =
                RecipeTransferPlanner.availability(recipe, List.of(
                        resource(Items.GLASS, 2),
                        resource(Items.IRON_INGOT, 3),
                        resource(Items.REDSTONE, 2),
                        resource(Items.CHEST, 1)));

        assertFalse(availability.canCraft());
        assertFalse(availability.isAvailable(7));
        assertEquals(8, Integer.bitCount(availability.availableMask()));
    }

    private static ShapedRecipe shaped(int width, int height,
                                       Ingredient... ingredients) {
        return TestStacks.shapedRecipe(width, height, java.util.Arrays.asList(ingredients),
                new ItemStack(Items.STICK));
    }

    private static RecipeTransferPlanner.Resource resource(
            net.minecraft.world.item.Item item, long count) {
        return new RecipeTransferPlanner.Resource(new ItemStack(item), count);
    }
}
