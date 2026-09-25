package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.LinkerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public final class LinkerScreen extends HandledScreen<LinkerScreenHandler> {
    private static final int TEXT_COLOR = 0xFF404040;
    public LinkerScreen(LinkerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
        titleX = 8;
        titleY = 6;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = 73;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        drawVanillaPanel(context, x, y, x + backgroundWidth, y + backgroundHeight);
        context.fill(x + 5, y + 17, x + 171, y + 69, -4342339);
        drawSlot(context, x + 79, y + 22);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(context, x + 7 + column * 18, y + 83 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(context, x + 7 + column * 18, y + 141);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, TEXT_COLOR, false);
        context.drawText(textRenderer, playerInventoryTitle,
                playerInventoryTitleX, playerInventoryTitleY, TEXT_COLOR, false);
        Text status = !handler.hasAntenna() && !handler.installationSpaceAvailable()
                ? Text.translatable("screen.tristorage.antenna_needs_air")
                : (!handler.hasAntenna()
                ? Text.translatable("screen.tristorage.antenna_overworld_only")
                : (handler.antennaActive()
                ? Text.translatable("screen.tristorage.antenna_active")
                : Text.translatable("screen.tristorage.antenna_blocked")));
        int color = 0xFF000000 | (handler.antennaActive() ? 2390580 : 9059364);
        context.drawText(textRenderer, status,
                (backgroundWidth - textRenderer.getWidth(status)) / 2, 48, color, false);
    }

    private static void drawVanillaPanel(DrawContext context,
                                         int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, -13158601);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, -3750202);
        context.fill(left + 1, top + 1, right - 1, top + 2, -1);
        context.fill(left + 1, top + 1, left + 2, bottom - 1, -1);
        context.fill(left + 1, bottom - 2, right - 1, bottom - 1, -11184811);
        context.fill(right - 2, top + 1, right - 1, bottom - 1, -11184811);
    }

    private static void drawSlot(DrawContext context, int left, int top) {
        context.fill(left, top, left + 18, top + 18, -7631989);
        context.fill(left, top, left + 18, top + 1, -13158601);
        context.fill(left, top, left + 1, top + 18, -13158601);
        context.fill(left, top + 17, left + 18, top + 18, -1);
        context.fill(left + 17, top, left + 18, top + 18, -1);
    }
}
