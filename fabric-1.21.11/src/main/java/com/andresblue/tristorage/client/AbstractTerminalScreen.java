package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Exact 1.9 vanilla-style terminal presentation adapted to the 1.21 render API. */
abstract class AbstractTerminalScreen<H extends ScreenHandler> extends HandledScreen<H>
        implements TerminalScreenMarker {
    private static final int TEXT_COLOR = 0xFF404040;
    protected static final Identifier VANILLA_CHEST_TEXTURE =
            Identifier.of("minecraft", "textures/gui/container/generic_54.png");
    private static final Identifier[] CATEGORY_TABS_SELECTED = categoryTabTextures(true);
    private static final Identifier[] CATEGORY_TABS_UNSELECTED = categoryTabTextures(false);
    private static final int CATEGORY_TAB_COUNT = 7;
    private static final int SEARCH_DEBOUNCE_TICKS = 4;

    private final TerminalClientConfig config = TerminalClientConfig.get();
    private final List<ButtonWidget> categoryButtons = new ArrayList<>();
    private final List<ButtonWidget> configButtons = new ArrayList<>();

    private ButtonWidget previousCategoryButton;
    private ButtonWidget nextCategoryButton;
    private ButtonWidget previousButton;
    private ButtonWidget nextButton;
    private ButtonWidget depositButton;
    private ButtonWidget sortButton;
    private ButtonWidget settingsButton;
    private TextFieldWidget searchField;
    private String searchQuery = "";
    private int searchDebounce = -1;
    private boolean syncingSearch;
    private boolean settingsOpen;

    protected AbstractTerminalScreen(H handler, PlayerInventory inventory, Text title,
                                     int width) {
        super(handler, inventory, title);
        backgroundWidth = width;
        backgroundHeight = 222;
        titleX = 8;
        titleY = 6;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = 129;
    }

    protected abstract Layout layout();
    protected abstract int page();
    protected abstract int pageCount();
    protected abstract int storedTypes();
    protected abstract long totalItems();
    protected abstract int categoryIndex();
    protected abstract int categoryCount();
    protected abstract int categoryWindowStart();
    protected abstract TerminalFilter.CategoryMode categoryMode();
    protected abstract int sortMode();
    protected abstract long displayCount(int index);
    protected abstract int storageSlotStart();
    protected abstract int categorySlotStart();
    protected abstract void clickButton(int id);
    protected abstract void drawTerminalBackground(DrawContext context, float delta,
                                                   int mouseX, int mouseY);

    @Override
    protected void init() {
        super.init();
        Layout layout = layout();
        previousButton = addDrawableChild(button("<", x + layout.previousX(),
                y + layout.navigationY(), 18, 16, 0));
        nextButton = addDrawableChild(button(">", x + layout.nextX(),
                y + layout.navigationY(), 18, 16, 1));
        depositButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("button.tristorage.deposit_inventory"),
                        ignored -> clickButton(2))
                .dimensions(x + layout.actionX(), y + layout.depositY(),
                        layout.actionWidth(), 16).build());
        sortButton = addDrawableChild(ButtonWidget.builder(sortButtonText(),
                        ignored -> clickButton(3))
                .dimensions(x + layout.actionX(), y + layout.sortY(),
                        layout.actionWidth(), 16).build());
        settingsButton = addDrawableChild(ButtonWidget.builder(Text.literal("⚙"),
                        ignored -> toggleSettings())
                .dimensions(x + layout.settingsX(), y + layout.settingsY(), 16, 16).build());

        previousCategoryButton = addDrawableChild(ButtonWidget.builder(Text.literal("<"),
                        ignored -> cycleCategory(-1))
                .dimensions(x - 18, y - 19, 16, 16)
                .tooltip(Tooltip.of(Text.translatable("button.tristorage.previous_category")))
                .build());
        nextCategoryButton = addDrawableChild(ButtonWidget.builder(Text.literal(">"),
                        ignored -> cycleCategory(1))
                .dimensions(x + 177, y - 19, 16, 16)
                .tooltip(Tooltip.of(Text.translatable("button.tristorage.next_category")))
                .build());

        searchField = addDrawableChild(new TextFieldWidget(textRenderer,
                x + layout.searchX(), y + layout.searchY(), layout.searchWidth(), 14,
                Text.translatable("screen.tristorage.search")));
        searchField.setMaxLength(TerminalFilter.MAX_QUERY_LENGTH);
        searchField.setText(searchQuery);
        searchField.setPlaceholder(Text.translatable("screen.tristorage.search_hint"));
        searchField.setChangedListener(value -> {
            if (syncingSearch) return;
            searchQuery = value;
            searchDebounce = SEARCH_DEBOUNCE_TICKS;
        });

        for (int tab = 0; tab < CATEGORY_TAB_COUNT; tab++) {
            int visibleIndex = tab;
            ButtonWidget categoryButton = addDrawableChild(ButtonWidget.builder(Text.empty(),
                            ignored -> selectVisibleCategory(visibleIndex))
                    .dimensions(x + 3 + tab * 24, y - 28, 26, 32).build());
            // 1.20.1's button skin happened to resemble a creative tab. 1.21's does not,
            // so retain the accessible/clickable widget but render the real vanilla tab
            // sprite ourselves below.
            categoryButton.setAlpha(0.0f);
            categoryButtons.add(categoryButton);
        }

        createConfigButtons();
        clickButton(700 + config.categoryMode().ordinal());
        updateWidgetVisibility();
        updateCategoryButtons();
    }

    private ButtonWidget button(String label, int left, int top, int width, int height, int id) {
        return ButtonWidget.builder(Text.literal(label), ignored -> clickButton(id))
                .dimensions(left, top, width, height).build();
    }

    private void createConfigButtons() {
        int left = x + 11;
        int top = y + 34;
        configButtons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), ignored -> {
            config.toggleSearch();
            if (!config.searchEnabled()) clearSearch();
            applyConfigChange();
        }).dimensions(left, top, 154, 16).build()));
        configButtons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), ignored -> {
            config.toggleCategories();
            if (!config.categoriesEnabled()) clickButton(703);
            applyConfigChange();
        }).dimensions(left, top + 20, 154, 16).build()));
        configButtons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), ignored -> {
            config.cycleCategoryMode();
            clickButton(700 + config.categoryMode().ordinal());
            applyConfigChange();
        }).dimensions(left, top + 40, 154, 16).build()));
        configButtons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), ignored -> {
            config.toggleWheelPaging();
            applyConfigChange();
        }).dimensions(left, top + 60, 154, 16).build()));
        configButtons.add(addDrawableChild(ButtonWidget.builder(Text.empty(), ignored -> {
            config.cycleThreshold();
            if (!categoriesVisible()) clickButton(703);
            applyConfigChange();
        }).dimensions(left, top + 80, 154, 16).build()));
        updateConfigButtonText();
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        previousButton.active = page() > 0;
        nextButton.active = page() + 1 < pageCount();
        sortButton.setMessage(sortButtonText());
        if (searchDebounce > 0) {
            searchDebounce--;
        } else if (searchDebounce == 0) {
            searchDebounce = -1;
            syncSearchToServer();
        }
        updateWidgetVisibility();
    }

    private void syncSearchToServer() {
        TerminalClientNetworking.sendFilter(handler.syncId, searchQuery, config.categoryMode());
    }

    private void clearSearch() {
        searchQuery = "";
        syncingSearch = true;
        searchField.setText("");
        syncingSearch = false;
        clickButton(6);
    }

    private void selectVisibleCategory(int visibleIndex) {
        int target = categoryWindowStart() + visibleIndex;
        int delta = target - categoryIndex();
        int button = delta < 0 ? 4 : 5;
        for (int index = 0; index < Math.abs(delta); index++) clickButton(button);
    }

    private void cycleCategory(int direction) {
        if (categoryCount() <= 1) return;
        if (direction < 0 && categoryIndex() == 0) {
            for (int index = 1; index < categoryCount(); index++) clickButton(5);
        } else if (direction > 0 && categoryIndex() + 1 >= categoryCount()) {
            for (int index = 1; index < categoryCount(); index++) clickButton(4);
        } else {
            clickButton(direction < 0 ? 4 : 5);
        }
    }

    private boolean categoriesVisible() {
        return !settingsOpen && config.categoriesEnabled()
                && (config.categoryThreshold() == 0
                || storedTypes() >= config.categoryThreshold());
    }

    private void toggleSettings() {
        settingsOpen = !settingsOpen;
        if (settingsOpen) searchField.setFocused(false);
        updateWidgetVisibility();
    }

    private void applyConfigChange() {
        updateConfigButtonText();
        updateWidgetVisibility();
        updateCategoryButtons();
    }

    private void updateWidgetVisibility() {
        boolean normal = !settingsOpen;
        previousButton.visible = normal;
        nextButton.visible = normal;
        depositButton.visible = normal;
        sortButton.visible = normal;
        searchField.setVisible(normal && config.searchEnabled());
        for (ButtonWidget button : configButtons) button.visible = settingsOpen;
        updateCategoryButtons();
    }

    private void updateCategoryButtons() {
        boolean visible = categoriesVisible();
        previousCategoryButton.visible = visible;
        nextCategoryButton.visible = visible;
        previousCategoryButton.active = categoryCount() > 1;
        nextCategoryButton.active = categoryCount() > 1;
        for (int tab = 0; tab < categoryButtons.size(); tab++) {
            ButtonWidget button = categoryButtons.get(tab);
            int category = categoryWindowStart() + tab;
            button.visible = visible && category < categoryCount();
            if (button.visible) {
                ItemStack icon = categoryIcon(tab);
                button.setTooltip(icon.isEmpty() ? null : Tooltip.of(icon.getName()));
            }
        }
    }

    private void updateConfigButtonText() {
        if (configButtons.isEmpty()) return;
        configButtons.get(0).setMessage(toggleText("config.tristorage.search", config.searchEnabled()));
        configButtons.get(1).setMessage(toggleText("config.tristorage.categories", config.categoriesEnabled()));
        configButtons.get(2).setMessage(Text.translatable("config.tristorage.category_mode",
                Text.translatable(config.categoryMode() == TerminalFilter.CategoryMode.TYPE
                        ? "config.tristorage.mode_type" : "config.tristorage.mode_mod")));
        configButtons.get(3).setMessage(toggleText("config.tristorage.wheel", config.wheelPagingEnabled()));
        Text threshold = config.categoryThreshold() == 0
                ? Text.translatable("config.tristorage.always")
                : Text.literal(Integer.toString(config.categoryThreshold()));
        configButtons.get(4).setMessage(Text.translatable("config.tristorage.threshold", threshold));
    }

    private static Text toggleText(String key, boolean enabled) {
        return Text.translatable(key,
                Text.translatable(enabled ? "config.tristorage.on" : "config.tristorage.off"));
    }

    @Override
    protected final void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        drawTerminalBackground(context, delta, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // DrawContext batches GUI elements in 1.21. A separate root layer is required
        // here; otherwise the panel submitted by drawBackground can cover dark text
        // while white text outside the panel remains visible.
        context.createNewRootLayer();
        Layout layout = layout();
        Text heading = categoriesVisible() ? selectedCategoryTitle() : title;
        context.drawText(textRenderer, heading, titleX, titleY, TEXT_COLOR, false);
        if (categoriesVisible()) {
            String categoryPosition = (categoryIndex() + 1) + "/" + categoryCount();
            context.drawText(textRenderer, categoryPosition,
                    89 - textRenderer.getWidth(categoryPosition) / 2, -35, 0xFFFFFFFF, false);
        }
        context.drawText(textRenderer, playerInventoryTitle,
                playerInventoryTitleX, playerInventoryTitleY, TEXT_COLOR, false);
        String pageText = (page() + 1) + " / " + pageCount();
        context.drawText(textRenderer, pageText,
                layout.pageCenterX() - textRenderer.getWidth(pageText) / 2,
                layout.pageY(), TEXT_COLOR, false);
        context.drawText(textRenderer, Text.translatable("screen.tristorage.types_short", storedTypes()),
                layout.statsX(), layout.typesY(), TEXT_COLOR, false);
        context.drawText(textRenderer, Text.translatable("screen.tristorage.items_short", totalItems()),
                layout.statsX(), layout.itemsY(), TEXT_COLOR, false);
        drawAdditionalForeground(context, mouseX, mouseY);
        if (settingsOpen) {
            context.drawText(textRenderer, Text.translatable("screen.tristorage.settings"),
                    11, 22, TEXT_COLOR, false);
        }
    }

    protected void drawAdditionalForeground(DrawContext context, int mouseX, int mouseY) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMain(context, mouseX, mouseY, delta);
        if (categoriesVisible()) drawCategoryTabs(context);
        if (settingsOpen) drawSettingsOverlay(context, mouseX, mouseY, delta);
        renderCursorStack(context, mouseX, mouseY);
        renderLetGoTouchStack(context);
    }

    private void drawCategoryTabs(DrawContext context) {
        context.createNewRootLayer();
        int selectedVisible = categoryIndex() - categoryWindowStart();

        // Draw non-selected tabs first so the selected tab naturally overlaps its
        // neighbours, matching the creative inventory's connected tab strip.
        for (int tab = 0; tab < categoryButtons.size(); tab++) {
            ButtonWidget button = categoryButtons.get(tab);
            if (!button.visible || tab == selectedVisible) continue;
            drawCategoryTab(context, button, tab, false);
        }

        if (selectedVisible >= 0 && selectedVisible < categoryButtons.size()) {
            ButtonWidget selected = categoryButtons.get(selectedVisible);
            if (selected.visible) drawCategoryTab(context, selected, selectedVisible, true);
        }
    }

    private void drawCategoryTab(DrawContext context, ButtonWidget button,
                                 int visibleIndex, boolean selected) {
        Identifier texture = (selected ? CATEGORY_TABS_SELECTED : CATEGORY_TABS_UNSELECTED)
                [visibleIndex];
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, texture,
                button.getX(), button.getY(), 26, 32);
        ItemStack icon = categoryIcon(visibleIndex);
        if (!icon.isEmpty()) {
            context.drawItemWithoutEntity(icon, button.getX() + 5, button.getY() + 8);
        }
    }

    private void drawSettingsOverlay(DrawContext context, int mouseX, int mouseY, float delta) {
        context.createNewRootLayer();
        drawVanillaPanel(context, x + 5, y + 16, x + 171, y + 217);
        // The crafting terminal owns real slots on the right. Cover that region too;
        // hidden slots must never remain visible or appear interactive behind Config.
        drawVanillaPanel(context, x + 176, y, x + backgroundWidth, y + backgroundHeight);
        context.drawText(textRenderer, GuiText.of(Text.translatable("screen.tristorage.settings")),
                x + 11, y + 22, TEXT_COLOR, false);
        for (ButtonWidget button : configButtons) {
            if (button.visible) button.render(context, mouseX, mouseY, delta);
        }
        if (settingsButton.visible) settingsButton.render(context, mouseX, mouseY, delta);
    }

    private static Identifier[] categoryTabTextures(boolean selected) {
        Identifier[] textures = new Identifier[CATEGORY_TAB_COUNT];
        for (int tab = 0; tab < CATEGORY_TAB_COUNT; tab++) {
            textures[tab] = Identifier.of("minecraft",
                    "container/creative_inventory/tab_top_"
                            + (selected ? "selected_" : "unselected_") + (tab + 1));
        }
        return textures;
    }

    private ItemStack categoryIcon(int visibleIndex) {
        int slotIndex = categorySlotStart() + visibleIndex;
        return slotIndex >= 0 && slotIndex < handler.slots.size()
                ? handler.slots.get(slotIndex).getStack() : ItemStack.EMPTY;
    }

    private Text selectedCategoryTitle() {
        int visible = categoryIndex() - categoryWindowStart();
        ItemStack icon = categoryIcon(visible);
        return icon.isEmpty() ? title : icon.getName();
    }

    @Override
    protected void drawSlot(DrawContext context, Slot slot, int mouseX, int mouseY) {
        int id = slot.id;
        if (id >= categorySlotStart() && id < categorySlotStart() + CATEGORY_TAB_COUNT) return;
        if (settingsOpen) return;
        super.drawSlot(context, slot, mouseX, mouseY);
    }

    @Override
    protected void drawSlots(DrawContext context, int mouseX, int mouseY) {
        super.drawSlots(context, mouseX, mouseY);
        if (settingsOpen) return;
        context.createNewRootLayer();
        for (int index = 0; index < TerminalScreenHandler.PAGE_SIZE; index++) {
            long count = displayCount(index);
            if (count <= 1) continue;
            Slot slot = handler.slots.get(storageSlotStart() + index);
            drawStoredCount(context, compact(count), slot.x, slot.y, count);
        }
    }

    private void drawStoredCount(DrawContext context, String label, int slotX, int slotY, long count) {
        int width = textRenderer.getWidth(label);
        float baseScale = count > 99 ? 0.75f : 1.0f;
        float scale = Math.min(baseScale, 16.0f / (width + 1.0f));
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(slotX + 17.0f, slotY + 17.0f - 9.0f * scale);
        context.getMatrices().scale(scale, scale);
        context.drawText(textRenderer, label, -width, 0, 0xFFFFFFFF, true);
        context.getMatrices().popMatrix();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (settingsOpen && input.key() == 256) {
            toggleSettings();
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            if (client != null && client.options.inventoryKey.matchesKey(input)) return true;
            if (searchField.keyPressed(input)) return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchField != null && searchField.isFocused() && searchField.charTyped(input)) return true;
        return super.charTyped(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!settingsOpen) return super.mouseClicked(click, doubled);
        if (settingsButton.mouseClicked(click, doubled)) return true;
        for (ButtonWidget button : configButtons) {
            if (button.mouseClicked(click, doubled)) return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (settingsOpen) return true;
        if (verticalAmount == 0.0) return super.mouseScrolled(mouseX, mouseY,
                horizontalAmount, verticalAmount);
        if (categoriesVisible() && mouseY >= y - 23 && mouseY < y + 2
                && mouseX >= x && mouseX < x + 176) {
            cycleCategory(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        if (config.wheelPagingEnabled()
                && (!searchField.isVisible() || !searchField.isMouseOver(mouseX, mouseY))
                && mouseX >= x && mouseX < x + backgroundWidth
                && mouseY >= y && mouseY < y + backgroundHeight) {
            clickButton(verticalAmount > 0 ? 0 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private Text sortButtonText() {
        return Text.translatable(switch (sortMode()) {
            case 1 -> "button.tristorage.sort_count";
            case 2 -> "button.tristorage.sort_recent";
            default -> "button.tristorage.sort_registry";
        });
    }

    protected static void drawChestTexture(DrawContext context, int left, int top) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, VANILLA_CHEST_TEXTURE,
                left, top, 0.0f, 0.0f, 176, 222, 256, 256);
    }

    protected static void drawVanillaPanel(DrawContext context,
                                           int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, -13158601);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, -3750202);
        context.fill(left + 1, top + 1, right - 1, top + 2, -1);
        context.fill(left + 1, top + 1, left + 2, bottom - 1, -1);
        context.fill(left + 1, bottom - 2, right - 1, bottom - 1, -11184811);
        context.fill(right - 2, top + 1, right - 1, bottom - 1, -11184811);
    }

    protected static void drawSlotFrame(DrawContext context, int left, int top) {
        context.fill(left, top, left + 18, top + 18, -7631989);
        context.fill(left, top, left + 18, top + 1, -13158601);
        context.fill(left, top, left + 1, top + 18, -13158601);
        context.fill(left, top + 17, left + 18, top + 18, -1);
        context.fill(left + 17, top, left + 18, top + 18, -1);
    }

    private static String compact(long count) {
        if (count >= 1_000_000_000L) return compactUnit(count, 1_000_000_000L, "b");
        if (count >= 1_000_000L) return compactUnit(count, 1_000_000L, "m");
        return count >= 1_000L ? compactUnit(count, 1_000L, "k") : Long.toString(count);
    }

    private static String compactUnit(long count, long unit, String suffix) {
        long whole = count / unit;
        long remainder = count % unit;
        if (whole < 10) {
            long decimals = remainder * 100 / unit;
            return whole + "." + (decimals < 10 ? "0" : "") + decimals + suffix;
        }
        if (whole < 100) return whole + "." + (remainder * 10 / unit) + suffix;
        return whole + suffix;
    }

    protected record Layout(int actionX, int actionWidth, int depositY, int sortY,
                            int previousX, int nextX, int navigationY,
                            int pageCenterX, int pageY, int statsX, int typesY, int itemsY,
                            int searchX, int searchY, int searchWidth,
                            int settingsX, int settingsY) {
    }
}
