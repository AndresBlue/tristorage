package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.item.RemoteTabletItem;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Shaped tablet upgrade that carries forward only a validated Linker address. */
public final class WirelessCraftingTerminalUpgradeRecipe extends WrappedShapedRecipe {
    public WirelessCraftingTerminalUpgradeRecipe(Parts parts) {
        super(parts);
    }

    @Override
    protected void finish(ItemStack result, List<ItemStack> grid) {
        for (ItemStack ingredient : grid) {
            if (ingredient.is(TriStorageMod.REMOTE_TABLET)) {
                RemoteTabletItem.copyValidatedLink(ingredient, result);
                break;
            }
        }
    }

    @Override
    protected RecipeSerializer<?> serializer() {
        return TriStorageMod.WIRELESS_CRAFTING_TERMINAL_RECIPE_SERIALIZER;
    }
}
