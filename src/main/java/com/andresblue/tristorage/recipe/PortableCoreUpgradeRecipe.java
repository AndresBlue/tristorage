package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.item.StorageCoreBlockItem;
import com.andresblue.tristorage.storage.PortableCoreData;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** A normal shaped recipe that carries a core's storage payload to its upgrade. */
public final class PortableCoreUpgradeRecipe extends WrappedShapedRecipe {
    public PortableCoreUpgradeRecipe(Parts parts) {
        super(parts);
    }

    @Override
    protected void finish(ItemStack result, List<ItemStack> grid) {
        for (ItemStack ingredient : grid) {
            if (ingredient.getItem() instanceof StorageCoreBlockItem) {
                PortableCoreData.applyTo(result, PortableCoreData.copyFrom(ingredient));
                break;
            }
        }
    }

    @Override
    protected RecipeSerializer<?> serializer() {
        return TriStorageMod.CORE_UPGRADE_RECIPE_SERIALIZER;
    }
}
