package mezz.jei.api.recipe.transfer;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import java.util.Optional;

public interface IRecipeTransferHandler<C extends AbstractContainerMenu, R> {
    Class<? extends C> getContainerClass();

    Optional<MenuType<C>> getMenuType();

    RecipeType<R> getRecipeType();

    IRecipeTransferError transferRecipe(C container, R recipe,
                                        IRecipeSlotsView recipeSlots,
                                        Player player,
                                        boolean maxTransfer,
                                        boolean doTransfer);
}
