package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.TerminalScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public final class TerminalScreen extends AbstractTerminalScreen<TerminalScreenHandler> {
    private static final Layout LAYOUT = new Layout(
            183, 79, 24, 44,
            183, 244, 108,
            222, 96,
            183, 65, 78,
            183, 5, 60,
            247, 4
    );

    public TerminalScreen(TerminalScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 270);
    }

    @Override
    protected Layout layout() {
        return LAYOUT;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(VANILLA_CHEST_TEXTURE, x, y, 0, 0, 176, 222);
        drawVanillaPanel(context, x + 176, y, x + 270, y + 128);
    }
}
