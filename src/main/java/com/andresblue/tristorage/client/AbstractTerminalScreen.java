package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractTerminalScreen<H extends TerminalScreenHandler>
        extends AbstractContainerScreen<H> implements TerminalScreenMarker {
    protected static final ResourceLocation VANILLA_CHEST_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    private static final float STORED_COUNT_Z = 325.0f;
    private static final float MAX_COUNT_WIDTH = 16.0f;
    private static final int CATEGORY_TAB_COUNT = 7;
    private static final int SEARCH_DEBOUNCE_TICKS = 4;

    private final TerminalClientConfig config = TerminalClientConfig.get();
    private final List<CategoryTabButton> categoryButtons = new ArrayList<>();
    private final List<Button> configButtons = new ArrayList<>();
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
        super(handler, inventory, title);
        this.imageWidth = backgroundWidth;
        imageHeight = 222;
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
        Layout layout = layout();
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> clickPageButton(0))
                .bounds(leftPos + layout.previousX(), topPos + layout.navigationY(), 18, 16)
                .build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> clickPageButton(1))
                .bounds(leftPos + layout.nextX(), topPos + layout.navigationY(), 18, 16)
                .build());
        depositButton = addRenderableWidget(Button.builder(
                        Component.translatable("button.tristorage.deposit_inventory"),
                        button -> clickPageButton(2))
                .bounds(leftPos + layout.actionX(), topPos + layout.depositY(),
                        layout.actionWidth(), 16)
                .build());
        sortButton = addRenderableWidget(Button.builder(sortButtonText(),
                        button -> clickPageButton(3))
                .bounds(leftPos + layout.actionX(), topPos + layout.sortY(),
                        layout.actionWidth(), 16)
                .build());
        settingsButton = addRenderableWidget(Button.builder(Component.literal("⚙"),
                        button -> toggleSettings())
                .bounds(leftPos + layout.settingsX(), topPos + layout.settingsY(), 16, 16)
                .build());
        previousCategoryButton = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> cycleCategory(-1))
                .bounds(leftPos - 18, topPos - 19, 16, 16)
                .tooltip(Tooltip.create(Component.translatable(
                        "button.tristorage.previous_category")))
                .build());
        nextCategoryButton = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> cycleCategory(1))
                .bounds(leftPos + 177, topPos - 19, 16, 16)
                .tooltip(Tooltip.create(Component.translatable(
                        "button.tristorage.next_category")))
                .build());

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
        createConfigButtons();
        updateWidgetVisibility();
        updateCategoryButtons();
        sendCurrentFilter(true);
    }

    private void createConfigButtons() {
        int left = leftPos + 11;
        int top = topPos + 34;
        configButtons.add(Button.builder(Component.empty(), button -> {
            config.toggleSearch();
            applyConfigChange();
        }).bounds(left, top, 154, 16).build());
        configButtons.add(Button.builder(Component.empty(), button -> {
            config.toggleCategories();
            applyConfigChange();
        }).bounds(left, top + 20, 154, 16).build());
        configButtons.add(Button.builder(Component.empty(), button -> {
            config.cycleCategoryMode();
            selectedCategory = TerminalFilter.ALL;
            applyConfigChange();
        }).bounds(left, top + 40, 154, 16).build());
        configButtons.add(Button.builder(Component.empty(), button -> {
            config.toggleWheelPaging();
            applyConfigChange();
        }).bounds(left, top + 60, 154, 16).build());
        configButtons.add(Button.builder(Component.empty(), button -> {
            config.cycleThreshold();
            selectedCategory = TerminalFilter.ALL;
            applyConfigChange();
        }).bounds(left, top + 80, 154, 16).build());
        updateConfigButtonText();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        searchField.tick();
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
            button.setTooltip(Tooltip.create(title));
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
            CreativeModeTab group = creativeGroup(category);
            return group == null ? Component.literal(category) : group.getDisplayName();
        }
        return Component.literal(FabricLoader.getInstance().getModContainer(category)
                .map(container -> container.getMetadata().getName())
                .orElse(category));
    }

    private ItemStack categoryIcon(String category) {
        if (TerminalFilter.ALL.equals(category)) {
            return Items.CHEST.getDefaultInstance();
        }
        if (TerminalFilter.UNCATEGORIZED.equals(category)) {
            return Items.BARRIER.getDefaultInstance();
        }
        if (serverCategoryMode == TerminalFilter.CategoryMode.TYPE) {
            CreativeModeTab group = creativeGroup(category);
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
        for (CreativeModeTab group : BuiltInRegistries.CREATIVE_MODE_TAB) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
            if (id != null && group.getType() == CreativeModeTab.Type.CATEGORY) {
                ItemStack icon = group.getIconItem();
                if (!icon.isEmpty()) {
                    modIconCache.putIfAbsent(id.getNamespace(), icon.copy());
                }
            }
        }
        // One registry pass for every visible mod tab, instead of one complete
        // pass per icon whenever the category window moves.
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            modIconCache.putIfAbsent(id.getNamespace(), item.getDefaultInstance());
        }
    }

    private CreativeModeTab creativeGroup(String category) {
        ResourceLocation id = ResourceLocation.tryParse(category);
        return id == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(id).orElse(null);
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
    protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
        Layout layout = layout();
        Component heading = categoriesVisible() ? categoryTitle(selectedCategory) : title;
        context.drawString(font, heading, titleLabelX, titleLabelY, 0x404040, false);
        if (categoriesVisible()) {
            String categoryPosition = (categoryIds.indexOf(selectedCategory) + 1)
                    + "/" + categoryIds.size();
            context.drawString(font, categoryPosition,
                    89 - font.width(categoryPosition) / 2,
                    -35, 0xFFFFFF, false);
        }
        context.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, 0x404040, false);
        String pageText = (menu.page() + 1) + " / " + menu.pageCount();
        context.drawString(font, pageText,
                layout.pageCenterX() - font.width(pageText) / 2,
                layout.pageY(), 0x404040, false);
        context.drawString(font,
                Component.translatable("screen.tristorage.types_short", menu.storedTypes()),
                layout.statsX(), layout.typesY(), 0x404040, false);
        context.drawString(font,
                Component.translatable("screen.tristorage.items_short", menu.totalItems()),
                layout.statsX(), layout.itemsY(), 0x404040, false);
        drawAdditionalForeground(context, mouseX, mouseY);
        if (!settingsOpen) {
            drawStoredCounts(context);
        }
        context.flush();
    }

    protected void drawAdditionalForeground(GuiGraphics context, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
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
            context.flush();
            for (CategoryTabButton button : categoryButtons) {
                if (button.visible) {
                    GuiGraphics isolated = new GuiGraphics(
                            minecraft, context.bufferSource());
                    // Disposable guard frames absorb a small number of rogue
                    // pop() calls from third-party item renderers. They are not
                    // balanced intentionally: the isolated context is thrown
                    // away immediately after this icon.
                    isolated.pose().pushPose();
                    isolated.pose().pushPose();
                    button.renderIconOverlay(isolated);
                    isolated.flush();
                }
            }
            renderTooltip(context, mouseX, mouseY);
        }
    }

    private void drawSettingsOverlay(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.pose().pushPose();
        context.pose().translate(0, 0, 500);
        drawVanillaPanel(context, leftPos + 5, topPos + 16, leftPos + 171, topPos + 217);
        context.drawString(font, Component.translatable("screen.tristorage.settings"),
                leftPos + 11, topPos + 22, 0x404040, false);
        for (Button button : configButtons) {
            button.render(context, mouseX, mouseY, delta);
        }
        context.pose().popPose();
    }

    /**
     * Keeps the category frame and its item in the same widget render pass.
     * Calling {@code super} is intentional: resource packs and GUI theming
     * mods can skin the normal button before TriStorage adds the icon.
     */
    private static final class CategoryTabButton extends Button {
        private ItemStack icon = ItemStack.EMPTY;
        private String categoryId = "";
        private boolean selected;

        private CategoryTabButton(int x, int y, OnPress action) {
            super(x, y, 22, 23, Component.empty(), action,
                    DEFAULT_NARRATION);
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
        protected void renderWidget(GuiGraphics context, int mouseX,
                                    int mouseY, float delta) {
            super.renderWidget(context, mouseX, mouseY, delta);
        }

        private void renderIconOverlay(GuiGraphics context) {
            if (icon.isEmpty()) {
                return;
            }
            if (selected) {
                context.fill(getX() + 2, getY() + 20,
                        getX() + 20, getY() + 22, 0xFFFFFFFF);
            }
            context.renderItem(icon, getX() + 3, getY() + 3);
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
            for (Button configButton : configButtons) {
                if (configButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            if (mouseX >= leftPos + 5 && mouseX < leftPos + 171
                    && mouseY >= topPos + 16 && mouseY < topPos + 217) {
                return true;
            }
        }
        if (pendingFilterSequence >= 0
                && mouseX >= leftPos + 7 && mouseX < leftPos + 171
                && mouseY >= topPos + 17 && mouseY < topPos + 127) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (settingsOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            toggleSettings();
            return true;
        }
        if (searchField != null && searchField.isFocused()
                && minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
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
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private Component sortButtonText() {
        return Component.translatable(switch (menu.sortMode()) {
            case 1 -> "button.tristorage.sort_count";
            case 2 -> "button.tristorage.sort_recent";
            default -> "button.tristorage.sort_registry";
        });
    }

    private void drawStoredCounts(GuiGraphics context) {
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

            context.pose().pushPose();
            context.pose().translate(rightEdge, drawY, STORED_COUNT_Z);
            context.pose().scale(scale, scale, 1.0f);
            context.drawString(font, label, -labelWidth, 0, 0xFFFFFF, true);
            context.pose().popPose();
        }
    }

    protected static void drawVanillaPanel(GuiGraphics context,
                                           int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, 0xFF373737);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFFC6C6C6);
        context.fill(left + 1, top + 1, right - 1, top + 2, 0xFFFFFFFF);
        context.fill(left + 1, top + 1, left + 2, bottom - 1, 0xFFFFFFFF);
        context.fill(left + 1, bottom - 2, right - 1, bottom - 1, 0xFF555555);
        context.fill(right - 2, top + 1, right - 1, bottom - 1, 0xFF555555);
    }

    protected static void drawSlotFrame(GuiGraphics context, int left, int top) {
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
