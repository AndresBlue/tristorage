package mezz.jei.api.registration;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.world.inventory.AbstractContainerMenu;

public interface IRecipeTransferRegistration {
    IRecipeTransferHandlerHelper getTransferHelper();

    <C extends AbstractContainerMenu, R> void addRecipeTransferHandler(
            IRecipeTransferHandler<C, R> handler, RecipeType<R> recipeType);
}
