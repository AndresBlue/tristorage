package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.TerminalScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
//? if >=1.21.6 {
/*import net.minecraft.client.renderer.RenderPipelines;
*///?}
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
        //? if >=1.21.6 {
        /*context.blit(RenderPipelines.GUI_TEXTURED, VANILLA_CHEST_TEXTURE, leftPos, topPos,
                0.0f, 0.0f, 176, 222, 256, 256);
        *///?} else {
        context.blit(VANILLA_CHEST_TEXTURE, leftPos, topPos, 0, 0, 176, 222);
        //?}
        drawVanillaPanel(context, leftPos + 176, topPos, leftPos + 270, topPos + 128);
    }
}
