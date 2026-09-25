package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.google.gson.JsonObject;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;

/** Shaped tablet upgrade that carries forward only a validated Linker address. */
public final class WirelessCraftingTerminalUpgradeRecipe extends ShapedRecipe {
    private WirelessCraftingTerminalUpgradeRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.getCategory(),
                recipe.getWidth(), recipe.getHeight(), recipe.getIngredients(),
                recipe.getOutput(DynamicRegistryManager.EMPTY).copy(),
                recipe.showNotification());
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory,
                           DynamicRegistryManager registryManager) {
        ItemStack result = super.craft(inventory, registryManager);
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack ingredient = inventory.getStack(slot);
            if (ingredient.isOf(TriStorageMod.REMOTE_TABLET)) {
                RemoteTabletItem.copyValidatedLink(ingredient, result);
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TriStorageMod.WIRELESS_CRAFTING_TERMINAL_RECIPE_SERIALIZER;
    }

    public static final class Serializer
            implements RecipeSerializer<WirelessCraftingTerminalUpgradeRecipe> {
        @Override
        public WirelessCraftingTerminalUpgradeRecipe read(Identifier id, JsonObject json) {
            return new WirelessCraftingTerminalUpgradeRecipe(
                    RecipeSerializer.SHAPED.read(id, json));
        }

        @Override
        public WirelessCraftingTerminalUpgradeRecipe read(Identifier id,
                                                           PacketByteBuf buf) {
            return new WirelessCraftingTerminalUpgradeRecipe(
                    RecipeSerializer.SHAPED.read(id, buf));
        }

        @Override
        public void write(PacketByteBuf buf,
                          WirelessCraftingTerminalUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED.write(buf, recipe);
        }
    }
}
