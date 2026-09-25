package mezz.jei.api.recipe.transfer;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

import java.util.Optional;

public interface IRecipeTransferHandler<C extends ScreenHandler, R> {
    Class<? extends C> getContainerClass();

    Optional<ScreenHandlerType<C>> getMenuType();

    RecipeType<R> getRecipeType();

    IRecipeTransferError transferRecipe(C container, R recipe,
                                        IRecipeSlotsView recipeSlots,
                                        PlayerEntity player,
                                        boolean maxTransfer,
                                        boolean doTransfer);
}
