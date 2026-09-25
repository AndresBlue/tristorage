package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.TerminalScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class TerminalScreen extends AbstractTerminalScreen<TerminalScreenHandler> {
    private static final Layout LAYOUT = new Layout(
            183, 79, 24, 44,
            183, 244, 108,
            222, 96,
            183, 65, 78,
            183, 5, 60,
            247, 4
    );

    public TerminalScreen(TerminalScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 270);
    }

    @Override
    protected Layout layout() {
        return LAYOUT;
    }

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
        context.blit(VANILLA_CHEST_TEXTURE, leftPos, topPos, 0, 0, 176, 222);
        drawVanillaPanel(context, leftPos + 176, topPos, leftPos + 270, topPos + 128);
    }
}
