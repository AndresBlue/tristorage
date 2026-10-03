package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.LinkerScreenHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class LinkerScreen extends CanvasScreen<LinkerScreenHandler> {
    public LinkerScreen(LinkerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = 73;
    }

    @Override
    protected void drawBackground(Canvas canvas) {
        canvas.panel(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        canvas.fill(leftPos + 5, topPos + 17, leftPos + 171, topPos + 69, 0xFFBDBDBD);
        canvas.slotFrame(leftPos + 79, topPos + 22);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                canvas.slotFrame(leftPos + 7 + column * 18, topPos + 83 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            canvas.slotFrame(leftPos + 7 + column * 18, topPos + 141);
        }
    }

    @Override
    protected void drawLabels(Canvas canvas, int mouseX, int mouseY) {
        canvas.text(font, title, titleLabelX, titleLabelY, 0xFF404040);
        canvas.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040);
        Component status = !menu.hasAntenna() && !menu.installationSpaceAvailable()
                ? Component.translatable("screen.tristorage.antenna_needs_air")
                : !menu.hasAntenna()
                ? Component.translatable("screen.tristorage.antenna_overworld_only")
                : menu.antennaActive()
                ? Component.translatable("screen.tristorage.antenna_active")
                : Component.translatable("screen.tristorage.antenna_blocked");
        int color = menu.antennaActive() ? 0xFF247A34 : 0xFF8A3C24;
        canvas.text(font, status, (imageWidth - font.width(status)) / 2, 48, color);
    }
}
