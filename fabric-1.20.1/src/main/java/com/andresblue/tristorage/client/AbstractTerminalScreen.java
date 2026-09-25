package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractTerminalScreen<H extends TerminalScreenHandler>
        extends HandledScreen<H> implements TerminalScreenMarker {
    protected static final Identifier VANILLA_CHEST_TEXTURE =
            new Identifier("minecraft", "textures/gui/container/generic_54.png");
    private static final float STORED_COUNT_Z = 325.0f;
    private static final float MAX_COUNT_WIDTH = 16.0f;
    private static final int CATEGORY_TAB_COUNT = 7;
    private static final int SEARCH_DEBOUNCE_TICKS = 4;

    private final TerminalClientConfig config = TerminalClientConfig.get();
    private final List<CategoryTabButton> categoryButtons = new ArrayList<>();
    private final List<ButtonWidget> configButtons = new ArrayList<>();
    private final Map<String, ItemStack> categoryIconCache = new HashMap<>();
    private final Map<String, ItemStack> modIconCache = new HashMap<>();
    private ButtonWidget previousCategoryButton;
    private ButtonWidget nextCategoryButton;
    private ButtonWidget previousButton;
    private ButtonWidget nextButton;
    private ButtonWidget depositButton;
    private ButtonWidget sortButton;
    private ButtonWidget settingsButton;
    private TextFieldWidget searchField;
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

    protected AbstractTerminalScreen(H handler, PlayerInventory inventory, Text title,
                                     int backgroundWidth) {
        super(handler, inventory, title);
        this.backgroundWidth = backgroundWidth;
        backgroundHeight = 222;
        titleX = 8;
        titleY = 6;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = 129;
    }

    protected abstract Layout layout();

    @Override
    protected void init() {
        super.init();
        TerminalClientNetworking.registerTerminalScreen(this);
        Layout layout = layout();
        previousButton = addDrawableChild(ButtonWidget.builder(Text.literal("<"),
                        button -> clickPageButton(0))
                .dimensions(x + layout.previousX(), y + layout.navigationY(), 18, 16)
                .build());
        nextButton = addDrawableChild(ButtonWidget.builder(Text.literal(">"),
                        button -> clickPageButton(1))
                .dimensions(x + layout.nextX(), y + layout.navigationY(), 18, 16)
                .build());
        depositButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("button.tristorage.deposit_inventory"),
                        button -> clickPageButton(2))
                .dimensions(x + layout.actionX(), y + layout.depositY(),
                        layout.actionWidth(), 16)
                .build());
        sortButton = addDrawableChild(ButtonWidget.builder(sortButtonText(),
                        button -> clickPageButton(3))
                .dimensions(x + layout.actionX(), y + layout.sortY(),
                        layout.actionWidth(), 16)
                .build());
        settingsButton = addDrawableChild(ButtonWidget.builder(Text.literal("⚙"),
                        button -> toggleSettings())
                .dimensions(x + layout.settingsX(), y + layout.settingsY(), 16, 16)
                .build());
        previousCategoryButton = addDrawableChild(ButtonWidget.builder(Text.literal("<"),
                        button -> cycleCategory(-1))
                .dimensions(x - 18, y - 19, 16, 16)
                .tooltip(Tooltip.of(Text.translatable(
                        "button.tristorage.previous_category")))
                .build());
        nextCategoryButton = addDrawableChild(ButtonWidget.builder(Text.literal(">"),
                        button -> cycleCategory(1))
                .dimensions(x + 177, y - 19, 16, 16)
                .tooltip(Tooltip.of(Text.translatable(
                        "button.tristorage.next_category")))
                .build());

        searchField = new TextFieldWidget(textRenderer,
                x + layout.searchX(), y + layout.searchY(),
                layout.searchWidth(), 14, Text.translatable("screen.tristorage.search"));
        searchField.setMaxLength(TerminalFilter.MAX_QUERY_LENGTH);
        searchField.setText(searchQuery);
        searchField.setSuggestion(Text.translatable("screen.tristorage.search_hint").getString());
        searchField.setChangedListener(value -> {
            searchQuery = value;
            searchField.setSuggestion(value.isEmpty()
                    ? Text.translatable("screen.tristorage.search_hint").getString()
                    : "");
            searchDebounce = SEARCH_DEBOUNCE_TICKS;
        });
        addDrawableChild(searchField);

        for (int tab = 0; tab < CATEGORY_TAB_COUNT; tab++) {
            final int buttonIndex = tab;
            CategoryTabButton button = addDrawableChild(new CategoryTabButton(
                    x + 5 + tab * 24, y - 22,
                    ignored -> selectVisibleCategory(buttonIndex)));
            categoryButtons.add(button);
        }
        createConfigButtons();
        updateWidgetVisibility();
        updateCategoryButtons();
        sendCurrentFilter(true);
    }

    private void createConfigButtons() {
        int left = x + 11;
        int top = y + 34;
        configButtons.add(ButtonWidget.builder(Text.empty(), button -> {
            config.toggleSearch();
            applyConfigChange();
        }).dimensions(left, top, 154, 16).build());
        configButtons.add(ButtonWidget.builder(Text.empty(), button -> {
            config.toggleCategories();
            applyConfigChange();
        }).dimensions(left, top + 20, 154, 16).build());
        configButtons.add(ButtonWidget.builder(Text.empty(), button -> {
            config.cycleCategoryMode();
            selectedCategory = TerminalFilter.ALL;
            applyConfigChange();
        }).dimensions(left, top + 40, 154, 16).build());
        configButtons.add(ButtonWidget.builder(Text.empty(), button -> {
            config.toggleWheelPaging();
            applyConfigChange();
        }).dimensions(left, top + 60, 154, 16).build());
        configButtons.add(ButtonWidget.builder(Text.empty(), button -> {
            config.cycleThreshold();
            selectedCategory = TerminalFilter.ALL;
            applyConfigChange();
        }).dimensions(left, top + 80, 154, 16).build());
        updateConfigButtonText();
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        searchField.tick();
        previousButton.active = pendingFilterSequence < 0 && handler.page() > 0;
        nextButton.active = pendingFilterSequence < 0
                && handler.page() + 1 < handler.pageCount();
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
                handler.syncId, sequence, query, mode, category);
    }

    private TerminalFilter.CategoryMode effectiveCategoryMode() {
        return config.categoriesEnabled()
                && handler.totalStoredTypes() >= config.categoryThreshold()
                ? config.categoryMode()
                : TerminalFilter.CategoryMode.NONE;
    }

    private void clickPageButton(int id) {
        if (pendingFilterSequence < 0
                && client != null && client.interactionManager != null) {
            client.interactionManager.clickButton(handler.syncId, id);
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
            Text title = categoryTitle(category);
            button.setMessage(Text.empty());
            button.setTooltip(Tooltip.of(title));
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

    private Text categoryTitle(String category) {
        if (TerminalFilter.ALL.equals(category)) {
            return Text.translatable("category.tristorage.all");
        }
        if (TerminalFilter.UNCATEGORIZED.equals(category)) {
            return Text.translatable("category.tristorage.uncategorized");
        }
        if (serverCategoryMode == TerminalFilter.CategoryMode.TYPE) {
            ItemGroup group = creativeGroup(category);
            return group == null ? Text.literal(category) : group.getDisplayName();
        }
        return Text.literal(FabricLoader.getInstance().getModContainer(category)
                .map(container -> container.getMetadata().getName())
                .orElse(category));
    }

    private ItemStack categoryIcon(String category) {
        if (TerminalFilter.ALL.equals(category)) {
            return Items.CHEST.getDefaultStack();
        }
        if (TerminalFilter.UNCATEGORIZED.equals(category)) {
            return Items.BARRIER.getDefaultStack();
        }
        if (serverCategoryMode == TerminalFilter.CategoryMode.TYPE) {
            ItemGroup group = creativeGroup(category);
            if (group != null) {
                return group.getIcon();
            }
        }
        if (serverCategoryMode == TerminalFilter.CategoryMode.MOD) {
            buildModIconCache();
            return modIconCache.getOrDefault(category, Items.BARRIER.getDefaultStack());
        }
        return Items.BARRIER.getDefaultStack();
    }

    private void buildModIconCache() {
        if (!modIconCache.isEmpty()) {
            return;
        }
        for (ItemGroup group : Registries.ITEM_GROUP) {
            Identifier id = Registries.ITEM_GROUP.getId(group);
            if (id != null && group.getType() == ItemGroup.Type.CATEGORY) {
                ItemStack icon = group.getIcon();
                if (!icon.isEmpty()) {
                    modIconCache.putIfAbsent(id.getNamespace(), icon.copy());
                }
            }
        }
        // One registry pass for every visible mod tab, instead of one complete
        // pass per icon whenever the category window moves.
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            Identifier id = Registries.ITEM.getId(item);
            modIconCache.putIfAbsent(id.getNamespace(), item.getDefaultStack());
        }
    }

    private ItemGroup creativeGroup(String category) {
        Identifier id = Identifier.tryParse(category);
        return id == null ? null : Registries.ITEM_GROUP.getOrEmpty(id).orElse(null);
    }

    private boolean categoriesVisible() {
        return !settingsOpen
                && effectiveCategoryMode() != TerminalFilter.CategoryMode.NONE
                && serverCategoryMode != TerminalFilter.CategoryMode.NONE;
    }

    private void toggleSettings() {
        settingsOpen = !settingsOpen;
        if (settingsOpen) {
            searchField.setFocused(false);
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
        configButtons.get(2).setMessage(Text.translatable(
                "config.tristorage.category_mode",
                Text.translatable(config.categoryMode() == TerminalFilter.CategoryMode.TYPE
                        ? "config.tristorage.mode_type" : "config.tristorage.mode_mod")));
        configButtons.get(3).setMessage(toggleText(
                "config.tristorage.wheel", config.wheelPagingEnabled()));
        Text threshold = config.categoryThreshold() == 0
                ? Text.translatable("config.tristorage.always")
                : Text.literal(Integer.toString(config.categoryThreshold()));
        configButtons.get(4).setMessage(Text.translatable(
                "config.tristorage.threshold", threshold));
    }

    private Text toggleText(String key, boolean enabled) {
        return Text.translatable(key, Text.translatable(enabled
                ? "config.tristorage.on" : "config.tristorage.off"));
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        Layout layout = layout();
        Text heading = categoriesVisible() ? categoryTitle(selectedCategory) : title;
        context.drawText(textRenderer, heading, titleX, titleY, 0x404040, false);
        if (categoriesVisible()) {
            String categoryPosition = (categoryIds.indexOf(selectedCategory) + 1)
                    + "/" + categoryIds.size();
            context.drawText(textRenderer, categoryPosition,
                    89 - textRenderer.getWidth(categoryPosition) / 2,
                    -35, 0xFFFFFF, false);
        }
        context.drawText(textRenderer, playerInventoryTitle,
                playerInventoryTitleX, playerInventoryTitleY, 0x404040, false);
        String pageText = (handler.page() + 1) + " / " + handler.pageCount();
        context.drawText(textRenderer, pageText,
                layout.pageCenterX() - textRenderer.getWidth(pageText) / 2,
                layout.pageY(), 0x404040, false);
        context.drawText(textRenderer,
                Text.translatable("screen.tristorage.types_short", handler.storedTypes()),
                layout.statsX(), layout.typesY(), 0x404040, false);
        context.drawText(textRenderer,
                Text.translatable("screen.tristorage.items_short", handler.totalItems()),
                layout.statsX(), layout.itemsY(), 0x404040, false);
        drawAdditionalForeground(context, mouseX, mouseY);
        if (!settingsOpen) {
            drawStoredCounts(context);
        }
        context.draw();
    }

    protected void drawAdditionalForeground(DrawContext context, int mouseX, int mouseY) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        if (settingsOpen) {
            drawSettingsOverlay(context, mouseX, mouseY, delta);
        } else {
            // Keep themed ButtonWidget frames in the normal screen pass, then
            // isolate every modded item renderer behind its own MatrixStack and
            // flush. Some pack items leave the supplied matrix unbalanced; a
            // shared DrawContext allowed one such icon to deform every tab that
            // followed it and produced an endless push/pop error stream.
            context.draw();
            for (CategoryTabButton button : categoryButtons) {
                if (button.visible) {
                    DrawContext isolated = new DrawContext(
                            client, context.getVertexConsumers());
                    // Disposable guard frames absorb a small number of rogue
                    // pop() calls from third-party item renderers. They are not
                    // balanced intentionally: the isolated context is thrown
                    // away immediately after this icon.
                    isolated.getMatrices().push();
                    isolated.getMatrices().push();
                    button.renderIconOverlay(isolated);
                    isolated.draw();
                }
            }
            drawMouseoverTooltip(context, mouseX, mouseY);
        }
    }

    private void drawSettingsOverlay(DrawContext context, int mouseX, int mouseY, float delta) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 500);
        drawVanillaPanel(context, x + 5, y + 16, x + 171, y + 217);
        context.drawText(textRenderer, Text.translatable("screen.tristorage.settings"),
                x + 11, y + 22, 0x404040, false);
        for (ButtonWidget button : configButtons) {
            button.render(context, mouseX, mouseY, delta);
        }
        context.getMatrices().pop();
    }

    /**
     * Keeps the category frame and its item in the same widget render pass.
     * Calling {@code super} is intentional: resource packs and GUI theming
     * mods can skin the normal button before TriStorage adds the icon.
     */
    private static final class CategoryTabButton extends ButtonWidget {
        private ItemStack icon = ItemStack.EMPTY;
        private String categoryId = "";
        private boolean selected;

        private CategoryTabButton(int x, int y, PressAction action) {
            super(x, y, 22, 23, Text.empty(), action,
                    DEFAULT_NARRATION_SUPPLIER);
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

        @Override
        protected void renderButton(DrawContext context, int mouseX,
                                    int mouseY, float delta) {
            super.renderButton(context, mouseX, mouseY, delta);
        }

        private void renderIconOverlay(DrawContext context) {
            if (icon.isEmpty()) {
                return;
            }
            if (selected) {
                context.fill(getX() + 2, getY() + 20,
                        getX() + 20, getY() + 22, 0xFFFFFFFF);
            }
            context.drawItem(icon, getX() + 3, getY() + 3);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchField != null && !searchField.isMouseOver(mouseX, mouseY)) {
            searchField.setFocused(false);
        }
        if (settingsOpen) {
            if (settingsButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            for (ButtonWidget configButton : configButtons) {
                if (configButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            if (mouseX >= x + 5 && mouseX < x + 171
                    && mouseY >= y + 16 && mouseY < y + 217) {
                return true;
            }
        }
        if (pendingFilterSequence >= 0
                && mouseX >= x + 7 && mouseX < x + 171
                && mouseY >= y + 17 && mouseY < y + 127) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button,
                                SlotActionType actionType) {
        if (slotId >= 0 && slotId < TerminalScreenHandler.PAGE_SIZE) {
            if (pendingFilterSequence < 0 && handler.hasAuthoritativePageState()) {
                TerminalClientNetworking.sendVirtualAction(
                        handler, slotId, button, actionType);
            }
            return;
        }
        super.onMouseClick(slot, slotId, button, actionType);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (settingsOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            toggleSettings();
            return true;
        }
        if (searchField != null && searchField.isFocused()
                && client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            // Keep the inventory key available as text input while the search
            // field owns focus. Clicking outside the field releases this guard.
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (settingsOpen) {
            return true;
        }
        if (amount == 0) {
            return super.mouseScrolled(mouseX, mouseY, amount);
        }
        if (categoriesVisible() && mouseY >= y - 23 && mouseY < y + 2
                && mouseX >= x && mouseX < x + 176) {
            cycleCategory(amount > 0 ? -1 : 1);
            return true;
        }
        if (pendingFilterSequence < 0 && config.wheelPagingEnabled()
                && !(searchField.visible && searchField.isMouseOver(mouseX, mouseY))
                && mouseX >= x && mouseX < x + backgroundWidth
                && mouseY >= y && mouseY < y + backgroundHeight) {
            clickPageButton(amount > 0 ? 0 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private Text sortButtonText() {
        return Text.translatable(switch (handler.sortMode()) {
            case 1 -> "button.tristorage.sort_count";
            case 2 -> "button.tristorage.sort_recent";
            default -> "button.tristorage.sort_registry";
        });
    }

    private void drawStoredCounts(DrawContext context) {
        for (int index = 0; index < TerminalScreenHandler.PAGE_SIZE; index++) {
            long count = handler.displayCount(index);
            if (count <= 1) {
                continue;
            }
            String label = compact(count);
            Slot slot = handler.slots.get(index);
            int labelWidth = textRenderer.getWidth(label);
            float baseScale = count > 99 ? 0.75f : 1.0f;
            float widthScale = MAX_COUNT_WIDTH / (labelWidth + 1.0f);
            float scale = Math.min(baseScale, widthScale);
            float rightEdge = slot.x + 17.0f;
            float drawY = slot.y + 17.0f - textRenderer.fontHeight * scale;

            context.getMatrices().push();
            context.getMatrices().translate(rightEdge, drawY, STORED_COUNT_Z);
            context.getMatrices().scale(scale, scale, 1.0f);
            context.drawText(textRenderer, label, -labelWidth, 0, 0xFFFFFF, true);
            context.getMatrices().pop();
        }
    }

    protected static void drawVanillaPanel(DrawContext context,
                                           int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, 0xFF373737);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFFC6C6C6);
        context.fill(left + 1, top + 1, right - 1, top + 2, 0xFFFFFFFF);
        context.fill(left + 1, top + 1, left + 2, bottom - 1, 0xFFFFFFFF);
        context.fill(left + 1, bottom - 2, right - 1, bottom - 1, 0xFF555555);
        context.fill(right - 2, top + 1, right - 1, bottom - 1, 0xFF555555);
    }

    protected static void drawSlotFrame(DrawContext context, int left, int top) {
        context.fill(left, top, left + 18, top + 18, 0xFF8B8B8B);
        context.fill(left, top, left + 18, top + 1, 0xFF373737);
        context.fill(left, top, left + 1, top + 18, 0xFF373737);
        context.fill(left, top + 17, left + 18, top + 18, 0xFFFFFFFF);
        context.fill(left + 17, top, left + 18, top + 18, 0xFFFFFFFF);
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
