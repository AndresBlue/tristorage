package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.CoreScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CoreScreen extends AbstractContainerScreen<CoreScreenHandler> {
    private Button withdrawButton;

    public CoreScreen(CoreScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        imageWidth = 176;
        imageHeight = 204;
        titleLabelX = 8;
        titleLabelY = 7;
        inventoryLabelX = 8;
        inventoryLabelY = 111;
    }

    @Override
    protected void init() {
        super.init();
        withdrawButton = addRenderableWidget(Button.builder(
                        Component.translatable("button.tristorage.withdraw_chests"),
                        button -> {
                            if (minecraft != null && minecraft.gameMode != null) {
                                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                            }
                        })
                .bounds(leftPos + 108, topPos + 34, 58, 16)
                .build());
    }

    @Override
    public void containerTick() {
        super.containerTick();
        withdrawButton.active = menu.installedChests() > 0;
    }

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
        drawVanillaPanel(context, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        context.fill(leftPos + 5, topPos + 17, leftPos + 171, topPos + 107, 0xFFBDBDBD);
        drawSlot(context, leftPos + 79, topPos + 34);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(context, leftPos + 7 + column * 18, topPos + 121 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(context, leftPos + 7 + column * 18, topPos + 179);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
        context.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        context.drawString(font, playerInventoryTitle,
                inventoryLabelX, inventoryLabelY, 0x404040, false);
        context.drawString(font,
                Component.translatable("screen.tristorage.insert_chests"),
                8, 21, 0x404040, false);
        context.drawString(font,
                Component.translatable("screen.tristorage.chests",
                        menu.installedChests(), menu.maxChests()),
                8, 68, 0x404040, false);
        context.drawString(font,
                Component.translatable("screen.tristorage.types",
                        menu.storedTypes(), menu.typeCapacity()),
                8, 80, 0x404040, false);
        context.drawString(font,
                Component.translatable("screen.tristorage.items",
                        menu.totalItems(), menu.itemCapacity()),
                8, 92, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        renderTooltip(context, mouseX, mouseY);
    }

    private static void drawVanillaPanel(GuiGraphics context,
                                         int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, 0xFF373737);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFFC6C6C6);
        context.fill(left + 1, top + 1, right - 1, top + 2, 0xFFFFFFFF);
        context.fill(left + 1, top + 1, left + 2, bottom - 1, 0xFFFFFFFF);
        context.fill(left + 1, bottom - 2, right - 1, bottom - 1, 0xFF555555);
        context.fill(right - 2, top + 1, right - 1, bottom - 1, 0xFF555555);
    }

    private static void drawSlot(GuiGraphics context, int left, int top) {
        context.fill(left, top, left + 18, top + 18, 0xFF8B8B8B);
        context.fill(left, top, left + 18, top + 1, 0xFF373737);
        context.fill(left, top, left + 1, top + 18, 0xFF373737);
        context.fill(left, top + 17, left + 18, top + 18, 0xFFFFFFFF);
        context.fill(left + 17, top, left + 18, top + 18, 0xFFFFFFFF);
    }
}
