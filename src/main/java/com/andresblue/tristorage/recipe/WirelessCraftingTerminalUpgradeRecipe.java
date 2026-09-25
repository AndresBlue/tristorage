package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Shaped tablet upgrade that carries forward only a validated Linker address. */
public final class WirelessCraftingTerminalUpgradeRecipe extends ShapedRecipe {
    private WirelessCraftingTerminalUpgradeRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(),
                recipe.getWidth(), recipe.getHeight(), recipe.getIngredients(),
                recipe.getResultItem(RegistryAccess.EMPTY).copy(),
                recipe.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingContainer inventory,
                           RegistryAccess registryManager) {
        ItemStack result = super.assemble(inventory, registryManager);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack ingredient = inventory.getItem(slot);
            if (ingredient.is(TriStorageMod.REMOTE_TABLET)) {
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
        public WirelessCraftingTerminalUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new WirelessCraftingTerminalUpgradeRecipe(
                    RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public WirelessCraftingTerminalUpgradeRecipe fromNetwork(ResourceLocation id,
                                                           FriendlyByteBuf buf) {
            return new WirelessCraftingTerminalUpgradeRecipe(
                    RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf,
                          WirelessCraftingTerminalUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
        }
    }
}
