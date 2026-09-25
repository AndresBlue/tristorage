package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import com.google.gson.JsonObject;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Shaped terminal upgrade that refuses to consume a terminal carrying any NBT. */
public final class CleanTerminalUpgradeRecipe extends ShapedRecipe {
    private CleanTerminalUpgradeRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.getCategory(),
                recipe.getWidth(), recipe.getHeight(), recipe.getIngredients(),
                recipe.getOutput(net.minecraft.registry.DynamicRegistryManager.EMPTY).copy(),
                recipe.showNotification());
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        if (!super.matches(inventory, world)) {
            return false;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack ingredient = inventory.getStack(slot);
            if (ingredient.isOf(TriStorageMod.TERMINAL.asItem())) {
                return TerminalUpgradeSafety.isClean(ingredient.getNbt());
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
        public CleanTerminalUpgradeRecipe read(Identifier id, JsonObject json) {
            return new CleanTerminalUpgradeRecipe(RecipeSerializer.SHAPED.read(id, json));
        }

        @Override
        public CleanTerminalUpgradeRecipe read(Identifier id, PacketByteBuf buf) {
            return new CleanTerminalUpgradeRecipe(RecipeSerializer.SHAPED.read(id, buf));
        }

        @Override
        public void write(PacketByteBuf buf, CleanTerminalUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED.write(buf, recipe);
        }
    }
}
