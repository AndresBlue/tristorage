package mezz.jei.api.gui.ingredient;

import mezz.jei.api.recipe.RecipeIngredientRole;

import java.util.List;

public interface IRecipeSlotsView {
    List<IRecipeSlotView> getSlotViews(RecipeIngredientRole role);
}
