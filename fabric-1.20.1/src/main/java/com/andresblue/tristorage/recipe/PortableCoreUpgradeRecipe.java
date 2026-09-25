package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.item.StorageCoreBlockItem;
import com.andresblue.tristorage.storage.PortableCoreData;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** A normal shaped recipe that carries a core's storage payload to its upgrade. */
public final class PortableCoreUpgradeRecipe extends ShapedRecipe {
    private PortableCoreUpgradeRecipe(ShapedRecipe recipe) {
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
            if (ingredient.getItem() instanceof StorageCoreBlockItem) {
                PortableCoreData.applyTo(result, PortableCoreData.copyFrom(ingredient));
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TriStorageMod.CORE_UPGRADE_RECIPE_SERIALIZER;
    }

    public static final class Serializer implements RecipeSerializer<PortableCoreUpgradeRecipe> {
        @Override
        public PortableCoreUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new PortableCoreUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public PortableCoreUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new PortableCoreUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, PortableCoreUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
        }
    }
}
