package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
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
    protected void drawBackground(Canvas canvas) {
        canvas.texture(VANILLA_CHEST_TEXTURE, leftPos, topPos, 176, 222);
        canvas.panel(leftPos + 176, topPos, leftPos + 320, topPos + 222);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                canvas.slotFrame(leftPos + 183 + column * 18, topPos + 19 + row * 18);
            }
        }
        canvas.slotFrame(leftPos + 271, topPos + 37);
    }

    @Override
    protected void drawAdditionalForeground(Canvas canvas, int mouseX, int mouseY) {
        canvas.text(font, Component.translatable("screen.tristorage.crafting"),
                184, 7, 0xFF404040);
        canvas.text(font, Component.literal("→"), 253, 42, 0xFF404040);
    }
}
