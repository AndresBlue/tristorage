package com.andresblue.tristorage.compat.emi;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.client.TerminalClientNetworking;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Optional EMI bridge; loaded only when EMI requests its Fabric entrypoints. */
public final class TriStorageEmiPlugin implements EmiPlugin {
    private static Field currentPageField;
    private static Field groupRecipeField;
    private static Field groupWidgetsField;
    private static Class<?> recipeFillButtonClass;
    private static Constructor<?> recipeFillButtonConstructor;
    private static Method recipeFillButtonBounds;
    private static Field recipeFillButtonCanFillField;
    private static Field recipeFillButtonTooltipField;

    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipeHandler(TriStorageMod.CRAFTING_TERMINAL_SCREEN_HANDLER,
                new CraftingHandler());
    }

    /**
     * Rebuilds only the affected fill controls after TriStorage's asynchronous
     * availability response. Reinitializing RecipeScreen also reinitializes
     * its old terminal screen and is prohibitively expensive in large packs.
     */
    public static void refreshRecipeFillButtons(ResourceLocation requestedRecipe) {
        Object screen = Minecraft.getInstance().screen;
        if (screen == null || !screen.getClass().getName()
                .equals("dev.emi.emi.screen.RecipeScreen")) {
            return;
        }
        try {
            if (currentPageField == null) {
                currentPageField = screen.getClass().getDeclaredField("currentPage");
                currentPageField.setAccessible(true);
            }
            Object pageValue = currentPageField.get(screen);
            if (!(pageValue instanceof List<?> groups)) {
                return;
            }
            for (Object group : groups) {
                if (group == null) {
                    continue;
                }
                ensureGroupFields(group.getClass());
                Object recipeValue = groupRecipeField.get(group);
                Object widgetsValue = groupWidgetsField.get(group);
                if (!(recipeValue instanceof EmiRecipe recipe)
                        || !(widgetsValue instanceof List<?> rawWidgets)
                        || (requestedRecipe != null
                        && !requestedRecipe.equals(recipe.getId()))) {
                    continue;
                }
                for (Object widget : rawWidgets) {
                    ensureFillButtonReflection();
                    if (!recipeFillButtonClass.isInstance(widget)) {
                        continue;
                    }
                    Object boundsValue = recipeFillButtonBounds.invoke(widget);
                    if (!(boundsValue instanceof Bounds bounds)) {
                        continue;
                    }
                    Object refreshed = recipeFillButtonConstructor.newInstance(
                            bounds.x(), bounds.y(), recipe);
                    // EMI keeps these two values as constructor-time caches.
                    // Updating them in place preserves the live WidgetGroup and
                    // its render/input iteration. Replacing widgets while EMI is
                    // rendering caused stale groups, overlapping category icons
                    // and work that grew with every recipe-fill click.
                    recipeFillButtonCanFillField.set(widget,
                            recipeFillButtonCanFillField.get(refreshed));
                    recipeFillButtonTooltipField.set(widget,
                            recipeFillButtonTooltipField.get(refreshed));
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // EMI is optional and may change internals between compatible
            // builds. The safe fallback is to keep the current button state.
        }
    }

    private static void ensureGroupFields(Class<?> groupClass)
            throws NoSuchFieldException {
        if (groupRecipeField != null && groupRecipeField.getDeclaringClass()
                == groupClass) {
            return;
        }
        groupRecipeField = groupClass.getField("recipe");
        groupWidgetsField = groupClass.getField("widgets");
    }

    private static void ensureFillButtonReflection()
            throws ReflectiveOperationException {
        if (recipeFillButtonClass != null) {
            return;
        }
        recipeFillButtonClass = Class.forName(
                "dev.emi.emi.api.widget.RecipeFillButtonWidget");
        recipeFillButtonConstructor = recipeFillButtonClass.getConstructor(
                int.class, int.class, EmiRecipe.class);
        recipeFillButtonBounds = recipeFillButtonClass.getMethod("getBounds");
        recipeFillButtonCanFillField = recipeFillButtonClass.getDeclaredField("canFill");
        recipeFillButtonCanFillField.setAccessible(true);
        recipeFillButtonTooltipField = recipeFillButtonClass.getDeclaredField("tooltip");
        recipeFillButtonTooltipField.setAccessible(true);
    }

    private static final class CraftingHandler
            implements EmiRecipeHandler<CraftingTerminalScreenHandler> {
        @Override
        public EmiPlayerInventory getInventory(
                AbstractContainerScreen<CraftingTerminalScreenHandler> screen) {
            return new EmiPlayerInventory(screen.getMenu()
                    .recipeTransferClientStacks().stream()
                    .map(EmiStack::of)
                    .toList());
        }

        @Override
        public boolean supportsRecipe(EmiRecipe recipe) {
            return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING
                    && recipe.getId() != null
                    && recipe.getBackingRecipe() instanceof CraftingRecipe crafting
                    && crafting.canCraftInDimensions(3, 3)
                    && crafting.getIngredients().stream()
                    .anyMatch(ingredient -> !ingredient.isEmpty());
        }

        @Override
        public boolean canCraft(EmiRecipe recipe,
                                EmiCraftContext<CraftingTerminalScreenHandler> context) {
            if (!supportsRecipe(recipe)) {
                return false;
            }
            TerminalClientNetworking.RecipeAvailability availability =
                    TerminalClientNetworking.recipeAvailability(
                            context.getScreenHandler(), recipe.getId());
            // Page-state/availability arrives asynchronously after the recipe
            // screen is constructed. Unknown is not the same as unavailable:
            // permit the first click and let the server's transactional fill
            // remain authoritative. The in-place refresh above will settle the
            // cached EMI button as soon as the compact response arrives.
            return availability == null || availability.canCraft();
        }

        @Override
        public boolean craft(EmiRecipe recipe,
                             EmiCraftContext<CraftingTerminalScreenHandler> context) {
            if (!supportsRecipe(recipe)) {
                return false;
            }
            TerminalClientNetworking.sendRecipeFill(
                    context.getScreenHandler().containerId,
                    recipe.getId(), context.getAmount());
            return true;
        }

        @Override
        public void render(EmiRecipe recipe,
                           EmiCraftContext<CraftingTerminalScreenHandler> context,
                           List<Widget> widgets, GuiGraphics draw) {
            if (!supportsRecipe(recipe)) {
                return;
            }
            TerminalClientNetworking.RecipeAvailability availability =
                    TerminalClientNetworking.recipeAvailability(
                            context.getScreenHandler(), recipe.getId());
            if (availability == null) {
                return;
            }
            CraftingRecipe crafting = (CraftingRecipe) recipe.getBackingRecipe();
            Map<EmiIngredient, Boolean> inputAvailability = new IdentityHashMap<>();
            List<EmiIngredient> inputs = recipe.getInputs();
            for (int index = 0; index < inputs.size(); index++) {
                EmiIngredient ingredient = inputs.get(index);
                if (!ingredient.isEmpty()) {
                    inputAvailability.put(ingredient,
                            isAvailable(crafting, index, availability));
                }
            }
            for (Widget widget : widgets) {
                if (!(widget instanceof SlotWidget slot) || slot.getRecipe() != null) {
                    continue;
                }
                EmiIngredient ingredient = slot.getStack();
                if (ingredient.isEmpty()
                        || inputAvailability.getOrDefault(ingredient, true)) {
                    continue;
                }
                Bounds bounds = slot.getBounds();
                draw.fill(bounds.x(), bounds.y(),
                        bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                        0x44FF0000);
            }
        }

        private boolean isAvailable(CraftingRecipe recipe, int emiInputIndex,
                                    TerminalClientNetworking.RecipeAvailability availability) {
            int inputIndex = emiInputIndex;
            if (recipe instanceof ShapedRecipe shaped) {
                int column = emiInputIndex % 3;
                int row = emiInputIndex / 3;
                if (column >= shaped.getWidth() || row >= shaped.getHeight()) {
                    return true;
                }
                inputIndex = column + row * shaped.getWidth();
            }
            return inputIndex < 9 && availability.isAvailable(inputIndex);
        }
    }
}
