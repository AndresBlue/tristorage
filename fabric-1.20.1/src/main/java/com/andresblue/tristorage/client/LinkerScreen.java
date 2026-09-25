package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.LinkerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public final class LinkerScreen extends HandledScreen<LinkerScreenHandler> {
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
        context.fill(x + 5, y + 17, x + 171, y + 69, 0xFFBDBDBD);
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
        context.drawText(textRenderer, title, titleX, titleY, 0x404040, false);
        context.drawText(textRenderer, playerInventoryTitle,
                playerInventoryTitleX, playerInventoryTitleY, 0x404040, false);
        Text status = !handler.hasAntenna() && !handler.installationSpaceAvailable()
                ? Text.translatable("screen.tristorage.antenna_needs_air")
                : !handler.hasAntenna()
                ? Text.translatable("screen.tristorage.antenna_overworld_only")
                : handler.antennaActive()
                ? Text.translatable("screen.tristorage.antenna_active")
                : Text.translatable("screen.tristorage.antenna_blocked");
        int color = handler.antennaActive() ? 0x247A34 : 0x8A3C24;
        context.drawText(textRenderer, status,
                (backgroundWidth - textRenderer.getWidth(status)) / 2,
                48, color, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private static void drawVanillaPanel(DrawContext context,
                                         int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, 0xFF373737);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFFC6C6C6);
        context.fill(left + 1, top + 1, right - 1, top + 2, 0xFFFFFFFF);
        context.fill(left + 1, top + 1, left + 2, bottom - 1, 0xFFFFFFFF);
        context.fill(left + 1, bottom - 2, right - 1, bottom - 1, 0xFF555555);
        context.fill(right - 2, top + 1, right - 1, bottom - 1, 0xFF555555);
    }

    private static void drawSlot(DrawContext context, int left, int top) {
        context.fill(left, top, left + 18, top + 18, 0xFF8B8B8B);
        context.fill(left, top, left + 18, top + 1, 0xFF373737);
        context.fill(left, top, left + 1, top + 18, 0xFF373737);
        context.fill(left, top + 17, left + 18, top + 18, 0xFFFFFFFF);
        context.fill(left + 17, top, left + 18, top + 18, 0xFFFFFFFF);
    }
}
