package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

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
                                  PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 320);
    }

    @Override
    protected Layout layout() {
        return LAYOUT;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(VANILLA_CHEST_TEXTURE, x, y, 0, 0, 176, 222);
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
                184, 7, 0x404040, false);
        context.drawText(textRenderer, Text.literal("→"), 253, 42, 0x404040, false);
    }
}
