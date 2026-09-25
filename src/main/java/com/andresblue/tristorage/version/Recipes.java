package com.andresblue.tristorage.version;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.Nullable;
//? if >=1.20.2 {
/*import net.minecraft.world.item.crafting.RecipeHolder;
*///?}

/**
 * Recipe lookups. Since Minecraft 1.20.2 a recipe no longer knows its id; the
 * manager hands out holders that pair the two.
 */
public final class Recipes {
    private Recipes() {
    }

    /** The crafting recipe loaded under an id, or null when there is none. */
    @Nullable
    public static CraftingRecipe crafting(RecipeManager manager, ResourceLocation id) {
        //? if >=1.20.2 {
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
}
