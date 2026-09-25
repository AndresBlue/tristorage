package com.andresblue.tristorage.version;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.Nullable;
//? if >=1.20.2 {
/*import net.minecraft.world.item.crafting.RecipeHolder;
*///?}
//? if >=1.21.2 {
/*import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.PlacementInfo;
*///?}

/**
 * Recipe lookups. Since Minecraft 1.20.2 a recipe no longer knows its id; the
 * manager hands out holders that pair the two. Since 1.21.2 recipes describe
 * their ingredients through placement info instead of a flat list.
 */
public final class Recipes {
    private static final int GRID = 3;

    private Recipes() {
    }

    /** The crafting recipe loaded under an id, or null when there is none. */
    @Nullable
    public static CraftingRecipe crafting(RecipeManager manager, ResourceLocation id) {
        //? if >=1.21.2 {
        /*return manager.byKey(ResourceKey.create(Registries.RECIPE, id)).map(RecipeHolder::value)
                .filter(CraftingRecipe.class::isInstance)
                .map(CraftingRecipe.class::cast)
                .orElse(null);
        *///?} elif >=1.20.2 {
        /*return manager.byKey(id).map(RecipeHolder::value)
                .filter(CraftingRecipe.class::isInstance)
                .map(CraftingRecipe.class::cast)
                .orElse(null);
        *///?} else {
        return manager.byKey(id)
                .filter(CraftingRecipe.class::isInstance)
                .map(CraftingRecipe.class::cast)
                .orElse(null);
        //?}
    }

    /**
     * The recipe's inputs as a 3x3 crafting grid sees them: a shaped recipe's
     * pattern row by row with null for an empty slot, otherwise one entry per
     * input. Null when the recipe does not fit a 3x3 grid.
     */
    @Nullable
    public static List<Ingredient> gridIngredients(CraftingRecipe recipe) {
        List<Ingredient> ingredients = new ArrayList<>();
        //? if >=1.21.2 {
        /*if (recipe instanceof ShapedRecipe shaped) {
            if (shaped.getWidth() > GRID || shaped.getHeight() > GRID) {
                return null;
            }
            for (Optional<Ingredient> ingredient : shaped.getIngredients()) {
                ingredients.add(ingredient.orElse(null));
            }
            return ingredients;
        }
        PlacementInfo placement = recipe.placementInfo();
        if (placement.isImpossibleToPlace() || placement.ingredients().size() > GRID * GRID) {
            return null;
        }
        ingredients.addAll(placement.ingredients());
        *///?} else {
        if (!recipe.canCraftInDimensions(GRID, GRID)) {
            return null;
        }
        for (Ingredient ingredient : recipe.getIngredients()) {
            ingredients.add(ingredient.isEmpty() ? null : ingredient);
        }
        //?}
        return ingredients;
    }

    /** Whether a 3x3 grid can craft the recipe and it asks for anything at all. */
    public static boolean fitsGrid(CraftingRecipe recipe) {
        List<Ingredient> ingredients = gridIngredients(recipe);
        return ingredients != null && ingredients.stream().anyMatch(Recipes::isPresent);
    }

    public static boolean isPresent(@Nullable Ingredient ingredient) {
        return ingredient != null && !ingredient.isEmpty();
    }

    /** The items an ingredient lists; custom ingredients may list none. */
    public static List<Item> candidateItems(Ingredient ingredient) {
        List<Item> items = new ArrayList<>();
        //? if >=1.21.2 {
        /*ingredient.items().map(Holder::value).forEach(items::add);
        *///?} else {
        for (ItemStack matching : ingredient.getItems()) {
            if (!matching.isEmpty()) {
                items.add(matching.getItem());
            }
        }
        //?}
        return items;
    }
}
