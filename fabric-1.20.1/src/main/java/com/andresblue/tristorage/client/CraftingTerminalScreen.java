package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CraftingTerminalScreen
        extends AbstractTerminalScreen<CraftingTerminalScreenHandler> {
    private static final Layout LAYOUT = new Layout(
            184, 108, 84, 104,
            184, 298, 176,
            247, 180,
            184, 130, 143,
            244, 19, 48,
            298, 18
    );

    public CraftingTerminalScreen(CraftingTerminalScreenHandler handler,
                                  Inventory inventory, Component title) {
        super(handler, inventory, title, 320);
    }

    @Override
    protected Layout layout() {
        return LAYOUT;
    }

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
        context.blit(VANILLA_CHEST_TEXTURE, leftPos, topPos, 0, 0, 176, 222);
        drawVanillaPanel(context, leftPos + 176, topPos, leftPos + 320, topPos + 222);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                drawSlotFrame(context, leftPos + 183 + column * 18, topPos + 19 + row * 18);
            }
        }
        drawSlotFrame(context, leftPos + 271, topPos + 37);
    }

    @Override
    protected void drawAdditionalForeground(GuiGraphics context, int mouseX, int mouseY) {
        context.drawString(font, Component.translatable("screen.tristorage.crafting"),
                184, 7, 0x404040, false);
        context.drawString(font, Component.literal("→"), 253, 42, 0x404040, false);
    }
}
