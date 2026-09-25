package com.andresblue.tristorage.compat.jei;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.client.TerminalClientNetworking;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.screen.RecipeTransferPlanner;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Optional JEI bridge; JEI remains neither bundled nor required. */
@JeiPlugin
public final class TriStorageJeiPlugin implements IModPlugin {
    private static final Identifier UID = TriStorageMod.id("jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new CraftingHandler(registration.getTransferHelper()), RecipeTypes.CRAFTING);
    }

    private static final class CraftingHandler implements
            IRecipeTransferHandler<CraftingTerminalScreenHandler, CraftingRecipe> {
        private final IRecipeTransferHandlerHelper transferHelper;

        private CraftingHandler(IRecipeTransferHandlerHelper transferHelper) {
            this.transferHelper = transferHelper;
        }

        @Override
        public Class<? extends CraftingTerminalScreenHandler> getContainerClass() {
            return CraftingTerminalScreenHandler.class;
        }

        @Override
        public Optional<ScreenHandlerType<CraftingTerminalScreenHandler>> getMenuType() {
            return Optional.of(TriStorageMod.CRAFTING_TERMINAL_SCREEN_HANDLER);
        }

        @Override
        public RecipeType<CraftingRecipe> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Override
        public IRecipeTransferError transferRecipe(
                CraftingTerminalScreenHandler container, CraftingRecipe recipe,
                IRecipeSlotsView recipeSlots, PlayerEntity player,
                boolean maxTransfer, boolean doTransfer) {
            if (!recipe.fits(3, 3) || recipe.getIngredients().stream()
                    .allMatch(ingredient -> ingredient.isEmpty())) {
                return null;
            }
            TerminalClientNetworking.RecipeAvailability availability =
                    TerminalClientNetworking.recipeAvailability(container, recipe.getId());
            if (availability == null) {
                return transferHelper.createUserErrorWithTooltip(Text.translatable(
                        "message.tristorage.recipe_availability_checking"));
            }
            if (!availability.canCraft()) {
                List<IRecipeSlotView> visibleInputs = recipeSlots.getSlotViews(
                        RecipeIngredientRole.INPUT);
                List<IRecipeSlotView> missing = new ArrayList<>();
                int visibleIndex = 0;
                for (int inputIndex = 0; inputIndex < recipe.getIngredients().size();
                     inputIndex++) {
                    if (recipe.getIngredients().get(inputIndex).isEmpty()) {
                        continue;
                    }
                    if (visibleIndex < visibleInputs.size()
                            && !availability.isAvailable(inputIndex)) {
                        missing.add(visibleInputs.get(visibleIndex));
                    }
                    visibleIndex++;
                }
                Text message = Text.translatable(
                        "message.tristorage.recipe_transfer_missing");
                return missing.isEmpty()
                        ? transferHelper.createUserErrorWithTooltip(message)
                        : transferHelper.createUserErrorForMissingSlots(message, missing);
            }
            if (doTransfer) {
                TerminalClientNetworking.sendRecipeFill(container.syncId,
                        recipe.getId(), maxTransfer
                                ? RecipeTransferPlanner.MAX_TRANSFER : 1);
            }
            return null;
        }
    }
}
