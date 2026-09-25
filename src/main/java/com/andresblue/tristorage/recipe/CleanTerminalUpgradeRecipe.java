package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Shaped terminal upgrade that refuses to consume a terminal carrying any data. */
public final class CleanTerminalUpgradeRecipe extends WrappedShapedRecipe {
    private CleanTerminalUpgradeRecipe(Parts parts) {
        super(parts);
    }

    @Override
    protected boolean accepts(List<ItemStack> grid) {
        for (ItemStack ingredient : grid) {
            if (ingredient.is(TriStorageMod.TERMINAL.asItem())) {
                return TerminalUpgradeSafety.isClean(ingredient);
            }
        }
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TriStorageMod.CRAFTING_TERMINAL_RECIPE_SERIALIZER;
    }

    public static final class Serializer
            extends WrappedShapedRecipe.Serializer<CleanTerminalUpgradeRecipe> {
        public Serializer() {
            super(CleanTerminalUpgradeRecipe::new);
        }
    }
}
