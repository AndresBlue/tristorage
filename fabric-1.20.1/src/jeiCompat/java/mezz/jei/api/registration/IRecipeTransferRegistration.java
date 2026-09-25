package mezz.jei.api.registration;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.screen.ScreenHandler;

public interface IRecipeTransferRegistration {
    IRecipeTransferHandlerHelper getTransferHelper();

    <C extends ScreenHandler, R> void addRecipeTransferHandler(
            IRecipeTransferHandler<C, R> handler, RecipeType<R> recipeType);
}
