package mezz.jei.api.recipe.transfer;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.text.Text;

import java.util.Collection;

public interface IRecipeTransferHandlerHelper {
    IRecipeTransferError createUserErrorWithTooltip(Text tooltipMessage);

    IRecipeTransferError createUserErrorForMissingSlots(
            Text tooltipMessage, Collection<IRecipeSlotView> missingItemSlots);
}
