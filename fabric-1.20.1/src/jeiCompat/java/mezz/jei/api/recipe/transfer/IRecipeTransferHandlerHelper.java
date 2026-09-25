package mezz.jei.api.recipe.transfer;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.network.chat.Component;
import java.util.Collection;

public interface IRecipeTransferHandlerHelper {
    IRecipeTransferError createUserErrorWithTooltip(Component tooltipMessage);

    IRecipeTransferError createUserErrorForMissingSlots(
            Component tooltipMessage, Collection<IRecipeSlotView> missingItemSlots);
}
