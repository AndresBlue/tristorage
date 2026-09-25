package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public final class CraftingTerminalScreen
        extends AbstractTerminalScreen<CraftingTerminalScreenHandler> {
    private static final Layout LAYOUT = new Layout(
            184, 108, 84, 104, 184, 298, 176, 247, 180,
            184, 130, 143, 244, 19, 48, 298, 18);

    public CraftingTerminalScreen(CraftingTerminalScreenHandler handler,
                                  PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 320);
    }

    @Override protected Layout layout() { return LAYOUT; }
    @Override protected int page() { return handler.page(); }
    @Override protected int pageCount() { return handler.pageCount(); }
    @Override protected int storedTypes() { return handler.storedTypes(); }
    @Override protected long totalItems() { return handler.totalItems(); }
    @Override protected int categoryIndex() { return handler.categoryIndex(); }
    @Override protected int categoryCount() { return handler.categoryCount(); }
    @Override protected int categoryWindowStart() { return handler.categoryWindowStart(); }
    @Override protected TerminalFilter.CategoryMode categoryMode() { return handler.categoryMode(); }
    @Override protected int sortMode() { return handler.sortMode(); }
    @Override protected long displayCount(int index) { return handler.displayCount(index); }
    @Override protected int storageSlotStart() { return CraftingTerminalScreenHandler.STORAGE_START; }
    @Override protected int categorySlotStart() { return CraftingTerminalScreenHandler.CATEGORY_START; }

    @Override
    protected void clickButton(int id) {
        if (client != null && client.interactionManager != null) {
            client.interactionManager.clickButton(handler.syncId, id);
        }
    }

    @Override
    protected void drawTerminalBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        drawChestTexture(context, x, y);
        drawVanillaPanel(context, x + 176, y, x + 320, y + 222);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                drawSlotFrame(context, x + 183 + column * 18, y + 19 + row * 18);
            }
        }
        drawSlotFrame(context, x + 271, y + 37);
    }

    @Override
    protected void drawAdditionalForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, Text.translatable("screen.tristorage.crafting"),
                184, 7, 0xFF404040, false);
        context.drawText(textRenderer, Text.literal("→"), 253, 42, 0xFF404040, false);
    }
}
