package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** Shaped terminal upgrade that refuses to consume a terminal carrying any NBT. */
public final class CleanTerminalUpgradeRecipe extends ShapedRecipe {
    private CleanTerminalUpgradeRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(),
                recipe.getWidth(), recipe.getHeight(), recipe.getIngredients(),
                recipe.getResultItem(net.minecraft.core.RegistryAccess.EMPTY).copy(),
                recipe.showNotification());
    }

    @Override
    public boolean matches(CraftingContainer inventory, Level world) {
        if (!super.matches(inventory, world)) {
            return false;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack ingredient = inventory.getItem(slot);
            if (ingredient.is(TriStorageMod.TERMINAL.asItem())) {
                return TerminalUpgradeSafety.isClean(ingredient.getTag());
            }
        }
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TriStorageMod.CRAFTING_TERMINAL_RECIPE_SERIALIZER;
    }

    public static final class Serializer implements RecipeSerializer<CleanTerminalUpgradeRecipe> {
        @Override
        public CleanTerminalUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new CleanTerminalUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public CleanTerminalUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new CleanTerminalUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, CleanTerminalUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
        }
    }
}
