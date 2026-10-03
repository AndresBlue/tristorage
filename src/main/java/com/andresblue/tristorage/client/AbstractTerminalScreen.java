package com.andresblue.tristorage.client;

import com.andresblue.tristorage.version.Ids;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.TerminalFilter;
import com.andresblue.tristorage.platform.Platform;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import com.andresblue.tristorage.version.CreativeTabs;
import com.andresblue.tristorage.version.GameRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} elif >=1.21.9 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=1.21.9 {
/*import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
*///?}

abstract class AbstractTerminalScreen<H extends TerminalScreenHandler>
        extends CanvasScreen<H> implements TerminalScreenMarker {
    protected static final ResourceLocation VANILLA_CHEST_TEXTURE =
            Ids.of("minecraft", "textures/gui/container/generic_54.png");
    private static final float MAX_COUNT_WIDTH = 16.0f;
    private static final int CATEGORY_TAB_COUNT = 7;
    private static final int SEARCH_DEBOUNCE_TICKS = 4;

    private final TerminalClientConfig config = TerminalClientConfig.get();
    private final List<CategoryTabButton> categoryButtons = new ArrayList<>();
    private final List<Button> configButtons = new ArrayList<>();
    private final List<Button> hintedButtons = new ArrayList<>();
    private final Map<String, ItemStack> categoryIconCache = new HashMap<>();
    private final Map<String, ItemStack> modIconCache = new HashMap<>();
    private Button previousCategoryButton;
    private Button nextCategoryButton;
    private Button previousButton;
    private Button nextButton;
    private Button depositButton;
    private Button sortButton;
    private Button settingsButton;
    private EditBox searchField;
    private String searchQuery = "";
    private String selectedCategory = TerminalFilter.ALL;
    private String lastSentFilter = "";
    private List<String> categoryIds = List.of(TerminalFilter.ALL);
    private TerminalFilter.CategoryMode serverCategoryMode = TerminalFilter.CategoryMode.NONE;
    private int categoryWindowStart;
    private int searchDebounce = -1;
    private int nextFilterSequence;
    private int pendingFilterSequence = -1;
    private TerminalFilter.CategoryMode categoryIconMode = TerminalFilter.CategoryMode.NONE;
    private boolean settingsOpen;

    protected AbstractTerminalScreen(H handler, Inventory inventory, Component title,
                                     int backgroundWidth) {
        super(handler, inventory, title, backgroundWidth, 222);
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = 129;
    }

    protected abstract Layout layout();

    @Override
    protected void init() {
        super.init();
        TerminalClientNetworking.registerTerminalScreen(this);
        // init runs again whenever the window is resized.
        categoryButtons.clear();
        configButtons.clear();
        hintedButtons.clear();
        Layout layout = layout();
        previousButton = addRenderableWidget(Widgets.button(
                leftPos + layout.previousX(), topPos + layout.navigationY(), 18, 16,
                Component.literal("<"), button -> clickPageButton(0)));
        nextButton = addRenderableWidget(Widgets.button(
                leftPos + layout.nextX(), topPos + layout.navigationY(), 18, 16,
                Component.literal(">"), button -> clickPageButton(1)));
        depositButton = addRenderableWidget(Widgets.button(
                leftPos + layout.actionX(), topPos + layout.depositY(), layout.actionWidth(), 16,
                Component.translatable("button.tristorage.deposit_inventory"),
                button -> clickPageButton(2)));
        sortButton = addRenderableWidget(Widgets.button(
                leftPos + layout.actionX(), topPos + layout.sortY(), layout.actionWidth(), 16,
                sortButtonText(), button -> clickPageButton(3)));
        settingsButton = addRenderableWidget(Widgets.button(
                leftPos + layout.settingsX(), topPos + layout.settingsY(), 16, 16,
                Component.literal("⚙"), button -> toggleSettings()));
        previousCategoryButton = addRenderableWidget(Widgets.button(
                leftPos - 18, topPos - 19, 16, 16,
                Component.literal("<"), button -> cycleCategory(-1)));
        Widgets.hint(previousCategoryButton,
                Component.translatable("button.tristorage.previous_category"));
        nextCategoryButton = addRenderableWidget(Widgets.button(
                leftPos + 177, topPos - 19, 16, 16,
                Component.literal(">"), button -> cycleCategory(1)));
        Widgets.hint(nextCategoryButton,
                Component.translatable("button.tristorage.next_category"));

        searchField = new EditBox(font,
                leftPos + layout.searchX(), topPos + layout.searchY(),
                layout.searchWidth(), 14, Component.translatable("screen.tristorage.search"));
        searchField.setMaxLength(TerminalFilter.MAX_QUERY_LENGTH);
        searchField.setValue(searchQuery);
        searchField.setSuggestion(Component.translatable("screen.tristorage.search_hint").getString());
        searchField.setResponder(value -> {
            searchQuery = value;
            searchField.setSuggestion(value.isEmpty()
                    ? Component.translatable("screen.tristorage.search_hint").getString()
                    : "");
            searchDebounce = SEARCH_DEBOUNCE_TICKS;
        });
        addRenderableWidget(searchField);

        for (int tab = 0; tab < CATEGORY_TAB_COUNT; tab++) {
            final int buttonIndex = tab;
            CategoryTabButton button = addRenderableWidget(new CategoryTabButton(
                    leftPos + 5 + tab * 24, topPos - 22,
                    ignored -> selectVisibleCategory(buttonIndex)));
            categoryButtons.add(button);
        }
        hintedButtons.addAll(categoryButtons);
        hintedButtons.add(previousCategoryButton);
        hintedButtons.add(nextCategoryButton);
        createConfigButtons();
        updateWidgetVisibility();
        updateCategoryButtons();
        sendCurrentFilter(true);
    }

    private void createConfigButtons() {
        int left = leftPos + 11;
        int top = topPos + 34;
        addConfigButton(left, top, () -> config.toggleSearch());
        addConfigButton(left, top + 20, () -> config.toggleCategories());
        addConfigButton(left, top + 40, () -> {
            config.cycleCategoryMode();
            selectedCategory = TerminalFilter.ALL;
        });
        addConfigButton(left, top + 60, () -> config.toggleWheelPaging());
        addConfigButton(left, top + 80, () -> {
            config.cycleThreshold();
            selectedCategory = TerminalFilter.ALL;
        });
        updateConfigButtonText();
    }

    private void addConfigButton(int x, int y, Runnable change) {
        configButtons.add(Widgets.button(x, y, 154, 16, Component.empty(), button -> {
            change.run();
            applyConfigChange();
        }));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        //? if <1.20.2 {
        searchField.tick();
        //?}
        previousButton.active = pendingFilterSequence < 0 && menu.page() > 0;
        nextButton.active = pendingFilterSequence < 0
                && menu.page() + 1 < menu.pageCount();
        sortButton.setMessage(sortButtonText());
        if (searchDebounce > 0) {
            searchDebounce--;
        } else if (searchDebounce == 0) {
            searchDebounce = -1;
            selectedCategory = TerminalFilter.ALL;
            sendCurrentFilter(true);
        }
        if (searchDebounce < 0) {
            sendCurrentFilter(false);
        }
        updateWidgetVisibility();
    }

    void acceptFilterState(int sequence, TerminalFilter.CategoryMode mode, String selected,
                           List<String> categories) {
        // FILTER_STATE can be emitted again when a storage refresh changes its
        // category list. Only the response for the newest client selection is
        // allowed to replace the tab model; a delayed response for an older
        // selection must never restore stale icons/categories.
        if (sequence != nextFilterSequence) {
            return;
        }
        pendingFilterSequence = -1;
        serverCategoryMode = mode;
        categoryIds = categories.isEmpty() ? List.of(TerminalFilter.ALL) : categories;
        selectedCategory = categoryIds.contains(selected) ? selected : TerminalFilter.ALL;
        ensureSelectedCategoryVisible();
        updateCategoryButtons();
    }

    private void sendCurrentFilter(boolean force) {
        TerminalFilter.CategoryMode mode = effectiveCategoryMode();
        String category = mode == TerminalFilter.CategoryMode.NONE
                ? TerminalFilter.ALL : selectedCategory;
        String query = config.searchEnabled() ? searchQuery : "";
        String fingerprint = query + '\u0000' + mode.name() + '\u0000' + category;
        if (!force && fingerprint.equals(lastSentFilter)) {
            return;
        }
        lastSentFilter = fingerprint;
        int sequence = ++nextFilterSequence;
        pendingFilterSequence = sequence;
        TerminalClientNetworking.sendFilter(
                menu.containerId, sequence, query, mode, category);
    }

    private TerminalFilter.CategoryMode effectiveCategoryMode() {
        return config.categoriesEnabled()
                && menu.totalStoredTypes() >= config.categoryThreshold()
                ? config.categoryMode()
                : TerminalFilter.CategoryMode.NONE;
    }

    private void clickPageButton(int id) {
        if (pendingFilterSequence < 0
                && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void selectVisibleCategory(int visibleIndex) {
        if (pendingFilterSequence >= 0) {
            return;
        }
        int categoryIndex = categoryWindowStart + visibleIndex;
        if (categoryIndex < 0 || categoryIndex >= categoryIds.size()) {
            return;
        }
        selectedCategory = categoryIds.get(categoryIndex);
        updateCategoryButtons();
        sendCurrentFilter(true);
    }

    private void cycleCategory(int direction) {
        if (categoryIds.size() <= 1) {
            return;
        }
        int current = Math.max(0, categoryIds.indexOf(selectedCategory));
        int next = Math.floorMod(current + direction, categoryIds.size());
        selectedCategory = categoryIds.get(next);
        ensureSelectedCategoryVisible();
        updateCategoryButtons();
        sendCurrentFilter(true);
    }

    private void ensureSelectedCategoryVisible() {
        int selected = Math.max(0, categoryIds.indexOf(selectedCategory));
        if (selected < categoryWindowStart) {
            categoryWindowStart = selected;
        } else if (selected >= categoryWindowStart + CATEGORY_TAB_COUNT) {
            categoryWindowStart = selected - CATEGORY_TAB_COUNT + 1;
        }
        categoryWindowStart = Math.max(0,
                Math.min(categoryWindowStart, Math.max(0, categoryIds.size() - CATEGORY_TAB_COUNT)));
    }

    private void updateCategoryButtons() {
        ensureSelectedCategoryVisible();
        boolean categoriesVisible = categoriesVisible();
        boolean iconModeChanged = categoryIconMode != serverCategoryMode;
        if (iconModeChanged) {
            categoryIconCache.clear();
            modIconCache.clear();
        }
        if (previousCategoryButton != null && nextCategoryButton != null) {
            previousCategoryButton.visible = categoriesVisible;
            nextCategoryButton.visible = categoriesVisible;
            previousCategoryButton.active = pendingFilterSequence < 0
                    && categoryIds.size() > 1;
            nextCategoryButton.active = previousCategoryButton.active;
        }
        for (int visible = 0; visible < categoryButtons.size(); visible++) {
            CategoryTabButton button = categoryButtons.get(visible);
            int categoryIndex = categoryWindowStart + visible;
            boolean valid = categoriesVisible && categoryIndex < categoryIds.size();
            button.visible = valid;
            if (!valid) {
                button.setCategory("", ItemStack.EMPTY, false);
                continue;
            }
            String category = categoryIds.get(categoryIndex);
            Component title = categoryTitle(category);
            button.setMessage(Component.empty());
            Widgets.hint(button, title);
            button.active = pendingFilterSequence < 0;
            if (iconModeChanged || !category.equals(button.categoryId())) {
                button.setCategory(category, stableCategoryIcon(category),
                        category.equals(selectedCategory));
            } else {
                button.setSelected(category.equals(selectedCategory));
            }
        }
        categoryIconMode = serverCategoryMode;
    }

    private ItemStack stableCategoryIcon(String category) {
        String cacheKey = serverCategoryMode.name() + ':' + category;
        ItemStack icon = categoryIconCache.computeIfAbsent(
                cacheKey, ignored -> categoryIcon(category));
        if (icon.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // Item groups are allowed to return a cached stack. Never hand that
        // shared mutable instance to the GUI renderer or to another mod.
        ItemStack snapshot = icon.copy();
        snapshot.setCount(1);
        return snapshot;
    }

    private Component categoryTitle(String category) {
        if (TerminalFilter.ALL.equals(category)) {
            return Component.translatable("category.tristorage.all");
        }
        if (TerminalFilter.UNCATEGORIZED.equals(category)) {
            return Component.translatable("category.tristorage.uncategorized");
        }
        if (serverCategoryMode == TerminalFilter.CategoryMode.TYPE) {
            CreativeModeTab group = CreativeTabs.byId(category);
            return group == null ? Component.literal(category) : group.getDisplayName();
        }
        return Component.literal(Platform.get().modName(category).orElse(category));
    }

    private ItemStack categoryIcon(String category) {
        if (TerminalFilter.ALL.equals(category)) {
            return Items.CHEST.getDefaultInstance();
        }
        if (TerminalFilter.UNCATEGORIZED.equals(category)) {
            return Items.BARRIER.getDefaultInstance();
        }
        if (serverCategoryMode == TerminalFilter.CategoryMode.TYPE) {
            CreativeModeTab group = CreativeTabs.byId(category);
            if (group != null) {
                return group.getIconItem();
            }
        }
        if (serverCategoryMode == TerminalFilter.CategoryMode.MOD) {
            buildModIconCache();
            return modIconCache.getOrDefault(category, Items.BARRIER.getDefaultInstance());
        }
        return Items.BARRIER.getDefaultInstance();
    }

    private void buildModIconCache() {
        if (!modIconCache.isEmpty()) {
            return;
        }
        for (CreativeModeTab group : CreativeTabs.categoryTabs()) {
            ItemStack icon = group.getIconItem();
            if (!icon.isEmpty()) {
                modIconCache.putIfAbsent(CreativeTabs.namespace(group), icon.copy());
            }
        }
        // One registry pass for every visible mod tab, instead of one complete
        // pass per icon whenever the category window moves.
        for (Item item : GameRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation id = GameRegistries.ITEM.getKey(item);
            modIconCache.putIfAbsent(id.getNamespace(), item.getDefaultInstance());
        }
    }

    private boolean categoriesVisible() {
        return !settingsOpen
                && effectiveCategoryMode() != TerminalFilter.CategoryMode.NONE
                && serverCategoryMode != TerminalFilter.CategoryMode.NONE;
    }

    private void toggleSettings() {
        settingsOpen = !settingsOpen;
        if (settingsOpen) {
            Widgets.setFocused(searchField, false);
        }
        updateWidgetVisibility();
    }

    private void applyConfigChange() {
        updateConfigButtonText();
        updateWidgetVisibility();
        updateCategoryButtons();
        sendCurrentFilter(true);
    }

    private void updateWidgetVisibility() {
        boolean normalControls = !settingsOpen;
        previousButton.visible = normalControls;
        nextButton.visible = normalControls;
        depositButton.visible = normalControls;
        sortButton.visible = normalControls;
        searchField.visible = normalControls && config.searchEnabled();
        updateCategoryButtons();
    }

    private void updateConfigButtonText() {
        if (configButtons.isEmpty()) {
            return;
        }
        configButtons.get(0).setMessage(toggleText(
                "config.tristorage.search", config.searchEnabled()));
        configButtons.get(1).setMessage(toggleText(
                "config.tristorage.categories", config.categoriesEnabled()));
        configButtons.get(2).setMessage(Component.translatable(
                "config.tristorage.category_mode",
                Component.translatable(config.categoryMode() == TerminalFilter.CategoryMode.TYPE
                        ? "config.tristorage.mode_type" : "config.tristorage.mode_mod")));
        configButtons.get(3).setMessage(toggleText(
                "config.tristorage.wheel", config.wheelPagingEnabled()));
        Component threshold = config.categoryThreshold() == 0
                ? Component.translatable("config.tristorage.always")
                : Component.literal(Integer.toString(config.categoryThreshold()));
        configButtons.get(4).setMessage(Component.translatable(
                "config.tristorage.threshold", threshold));
    }

    private Component toggleText(String key, boolean enabled) {
        return Component.translatable(key, Component.translatable(enabled
                ? "config.tristorage.on" : "config.tristorage.off"));
    }

    @Override
    protected void drawLabels(Canvas canvas, int mouseX, int mouseY) {
        Layout layout = layout();
        Component heading = categoriesVisible() ? categoryTitle(selectedCategory) : title;
        canvas.text(font, heading, titleLabelX, titleLabelY, 0xFF404040);
        if (categoriesVisible()) {
            String categoryPosition = (categoryIds.indexOf(selectedCategory) + 1)
                    + "/" + categoryIds.size();
            canvas.text(font, categoryPosition,
                    89 - font.width(categoryPosition) / 2, -35, 0xFFFFFFFF);
        }
        canvas.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040);
        String pageText = (menu.page() + 1) + " / " + menu.pageCount();
        canvas.text(font, pageText,
                layout.pageCenterX() - font.width(pageText) / 2, layout.pageY(), 0xFF404040);
        canvas.text(font,
                Component.translatable("screen.tristorage.types_short", menu.storedTypes()),
                layout.statsX(), layout.typesY(), 0xFF404040);
        canvas.text(font,
                Component.translatable("screen.tristorage.items_short", menu.totalItems()),
                layout.statsX(), layout.itemsY(), 0xFF404040);
        drawAdditionalForeground(canvas, mouseX, mouseY);
        if (!settingsOpen) {
            drawStoredCounts(canvas);
        }
        canvas.flush();
    }

    protected void drawAdditionalForeground(Canvas canvas, int mouseX, int mouseY) {
    }

    @Override
    protected boolean drawForeground(Canvas canvas, int mouseX, int mouseY, float delta) {
        if (settingsOpen) {
            drawSettingsOverlay(canvas, mouseX, mouseY, delta);
            return false;
        }
        // The themed button frames stay in the normal screen pass; each
        // category icon is drawn afterwards on a canvas of its own.
        canvas.flush();
        for (CategoryTabButton button : categoryButtons) {
            if (button.visible) {
                Canvas isolated = canvas.isolated(minecraft);
                button.drawIcon(isolated);
                isolated.flush();
            }
        }
        Widgets.drawHints(canvas, this, hintedButtons, mouseX, mouseY);
        return true;
    }

    private void drawSettingsOverlay(Canvas canvas, int mouseX, int mouseY, float delta) {
        canvas.beginOverlay();
        canvas.panel(leftPos + 5, topPos + 16, leftPos + 171, topPos + 217);
        canvas.text(font, Component.translatable("screen.tristorage.settings"),
                leftPos + 11, topPos + 22, 0xFF404040);
        for (Button button : configButtons) {
            canvas.widget(button, mouseX, mouseY, delta);
        }
        canvas.endOverlay();
    }

    /**
     * A category tab: a plain button frame, which resource packs and GUI
     * theming mods can skin, with the category's item drawn over it.
     */
    private static final class CategoryTabButton extends Button {
        private ItemStack icon = ItemStack.EMPTY;
        private String categoryId = "";
        private boolean selected;

        private CategoryTabButton(int x, int y, OnPress action) {
            //? if >=1.19.3 {
            super(x, y, 22, 23, Component.empty(), action, DEFAULT_NARRATION);
            //?} else {
            /*super(x, y, 22, 23, Component.empty(), action);
            *///?}
        }

        private String categoryId() {
            return categoryId;
        }

        private void setCategory(String categoryId, ItemStack icon,
                                 boolean selected) {
            this.categoryId = categoryId;
            this.icon = icon.isEmpty() ? ItemStack.EMPTY : icon.copy();
            this.selected = selected;
        }

        private void setSelected(boolean selected) {
            this.selected = selected;
        }

        // The tab is the button frame alone; its item is drawn on top later.
        //? if >=26.1 {
        /*@Override
        protected void extractContents(GuiGraphicsExtractor target, int mouseX, int mouseY,
                                       float delta) {
            extractDefaultSprite(target);
        }
        *///?} elif >=1.21.9 {
        /*@Override
        protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
            renderDefaultSprite(context);
        }
        *///?}

        private void drawIcon(Canvas canvas) {
            if (icon.isEmpty()) {
                return;
            }
            int x = Widgets.x(this);
            int y = Widgets.y(this);
            if (selected) {
                canvas.fill(x + 2, y + 20, x + 20, y + 22, 0xFFFFFFFF);
            }
            canvas.item(icon, x + 3, y + 3);
        }
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return clickTerminal(event.x(), event.y(),
                widget -> widget.mouseClicked(event, doubleClick))
                || super.mouseClicked(event, doubleClick);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickTerminal(mouseX, mouseY,
                widget -> widget.mouseClicked(mouseX, mouseY, button))
                || super.mouseClicked(mouseX, mouseY, button);
    }
    //?}

    /** Handles a click the terminal takes itself; false passes it to the screen. */
    private boolean clickTerminal(double mouseX, double mouseY, Predicate<Button> clickButton) {
        if (searchField != null && !searchField.isMouseOver(mouseX, mouseY)) {
            Widgets.setFocused(searchField, false);
        }
        if (settingsOpen) {
            if (clickButton.test(settingsButton)) {
                return true;
            }
            for (Button configButton : configButtons) {
                if (clickButton.test(configButton)) {
                    return true;
                }
            }
            if (mouseX >= leftPos + 5 && mouseX < leftPos + 171
                    && mouseY >= topPos + 16 && mouseY < topPos + 217) {
                return true;
            }
        }
        return pendingFilterSequence >= 0
                && mouseX >= leftPos + 7 && mouseX < leftPos + 171
                && mouseY >= topPos + 17 && mouseY < topPos + 127;
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button,
                                ClickType actionType) {
        if (slotId >= 0 && slotId < TerminalScreenHandler.PAGE_SIZE) {
            if (pendingFilterSequence < 0 && menu.hasAuthoritativePageState()) {
                TerminalClientNetworking.sendVirtualAction(
                        menu, slotId, button, actionType);
            }
            return;
        }
        super.slotClicked(slot, slotId, button, actionType);
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean keyPressed(KeyEvent event) {
        return pressTerminalKey(event.key(),
                minecraft != null && minecraft.options.keyInventory.matches(event))
                || super.keyPressed(event);
    }
    *///?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return pressTerminalKey(keyCode,
                minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))
                || super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    private boolean pressTerminalKey(int keyCode, boolean inventoryKey) {
        if (settingsOpen && keyCode == InputConstants.KEY_ESCAPE) {
            toggleSettings();
            return true;
        }
        // Keep the inventory key available as text input while the search
        // field owns focus. Clicking outside the field releases this guard.
        return searchField != null && searchField.isFocused() && inventoryKey;
    }

    //? if >=1.20.2 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double amount) {
        return scrollTerminal(mouseX, mouseY, amount)
                || super.mouseScrolled(mouseX, mouseY, horizontal, amount);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return scrollTerminal(mouseX, mouseY, amount)
                || super.mouseScrolled(mouseX, mouseY, amount);
    }
    //?}

    /** Handles the wheel over the terminal; false lets the screen handle it. */
    private boolean scrollTerminal(double mouseX, double mouseY, double amount) {
        if (settingsOpen) {
            return true;
        }
        if (amount == 0) {
            return false;
        }
        if (categoriesVisible() && mouseY >= topPos - 23 && mouseY < topPos + 2
                && mouseX >= leftPos && mouseX < leftPos + 176) {
            cycleCategory(amount > 0 ? -1 : 1);
            return true;
        }
        if (pendingFilterSequence < 0 && config.wheelPagingEnabled()
                && !(searchField.visible && searchField.isMouseOver(mouseX, mouseY))
                && mouseX >= leftPos && mouseX < leftPos + imageWidth
                && mouseY >= topPos && mouseY < topPos + imageHeight) {
            clickPageButton(amount > 0 ? 0 : 1);
            return true;
        }
        return false;
    }

    private Component sortButtonText() {
        return Component.translatable(switch (menu.sortMode()) {
            case 1 -> "button.tristorage.sort_count";
            case 2 -> "button.tristorage.sort_recent";
            default -> "button.tristorage.sort_registry";
        });
    }

    private void drawStoredCounts(Canvas canvas) {
        for (int index = 0; index < TerminalScreenHandler.PAGE_SIZE; index++) {
            long count = menu.displayCount(index);
            if (count <= 1) {
                continue;
            }
            String label = compact(count);
            Slot slot = menu.slots.get(index);
            int labelWidth = font.width(label);
            float baseScale = count > 99 ? 0.75f : 1.0f;
            float widthScale = MAX_COUNT_WIDTH / (labelWidth + 1.0f);
            float scale = Math.min(baseScale, widthScale);
            float rightEdge = slot.x + 17.0f;
            float drawY = slot.y + 17.0f - font.lineHeight * scale;
            canvas.countLabel(font, label, rightEdge, drawY, scale);
        }
    }

    private static String compact(long count) {
        if (count >= 1_000_000_000L) {
            return compactUnit(count, 1_000_000_000L, "b");
        }
        if (count >= 1_000_000L) {
            return compactUnit(count, 1_000_000L, "m");
        }
        if (count >= 1_000L) {
            return compactUnit(count, 1_000L, "k");
        }
        return Long.toString(count);
    }

    private static String compactUnit(long count, long unit, String suffix) {
        long whole = count / unit;
        long remainder = count % unit;
        if (whole < 10) {
            long decimals = remainder * 100 / unit;
            return whole + "." + twoDigits(decimals) + suffix;
        }
        if (whole < 100) {
            long decimal = remainder * 10 / unit;
            return whole + "." + decimal + suffix;
        }
        return whole + suffix;
    }

    private static String twoDigits(long value) {
        return value < 10 ? "0" + value : Long.toString(value);
    }

    protected record Layout(int actionX, int actionWidth, int depositY, int sortY,
                            int previousX, int nextX, int navigationY,
                            int pageCenterX, int pageY,
                            int statsX, int typesY, int itemsY,
                            int searchX, int searchY, int searchWidth,
                            int settingsX, int settingsY) {
    }
}
