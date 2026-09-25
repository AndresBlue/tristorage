package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.item.StorageCoreBlockItem;
import com.andresblue.tristorage.storage.PortableCoreData;
import com.google.gson.JsonObject;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;

/** A normal shaped recipe that carries a core's storage payload to its upgrade. */
public final class PortableCoreUpgradeRecipe extends ShapedRecipe {
    private PortableCoreUpgradeRecipe(ShapedRecipe recipe) {
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
        public PortableCoreUpgradeRecipe read(Identifier id, JsonObject json) {
            return new PortableCoreUpgradeRecipe(RecipeSerializer.SHAPED.read(id, json));
        }

        @Override
        public PortableCoreUpgradeRecipe read(Identifier id, PacketByteBuf buf) {
            return new PortableCoreUpgradeRecipe(RecipeSerializer.SHAPED.read(id, buf));
        }

        @Override
        public void write(PacketByteBuf buf, PortableCoreUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED.write(buf, recipe);
        }
    }
}
