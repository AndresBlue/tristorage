package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public final class TerminalScreen extends AbstractTerminalScreen<TerminalScreenHandler> {
    private static final Layout LAYOUT = new Layout(
            183, 79, 24, 44, 183, 244, 108, 222, 96,
            183, 65, 78, 183, 5, 60, 247, 4);

    public TerminalScreen(TerminalScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 270);
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
    @Override protected int storageSlotStart() { return 0; }
    @Override protected int categorySlotStart() { return TerminalScreenHandler.CATEGORY_START; }

    @Override
    protected void clickButton(int id) {
        if (client != null && client.interactionManager != null) {
            client.interactionManager.clickButton(handler.syncId, id);
        }
    }

    @Override
    protected void drawTerminalBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        drawChestTexture(context, x, y);
        drawVanillaPanel(context, x + 176, y, x + 270, y + 128);
    }
}
