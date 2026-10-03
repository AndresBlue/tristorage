package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.CoreScreenHandler;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CoreScreen extends CanvasScreen<CoreScreenHandler> {
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
        withdrawButton = addRenderableWidget(Widgets.button(
                leftPos + 108, topPos + 34, 58, 16,
                Component.translatable("button.tristorage.withdraw_chests"),
                button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                    }
                }));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        withdrawButton.active = menu.installedChests() > 0;
    }

    @Override
    protected void drawBackground(Canvas canvas) {
        canvas.panel(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        canvas.fill(leftPos + 5, topPos + 17, leftPos + 171, topPos + 107, 0xFFBDBDBD);
        canvas.slotFrame(leftPos + 79, topPos + 34);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                canvas.slotFrame(leftPos + 7 + column * 18, topPos + 121 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            canvas.slotFrame(leftPos + 7 + column * 18, topPos + 179);
        }
    }

    @Override
    protected void drawLabels(Canvas canvas, int mouseX, int mouseY) {
        canvas.text(font, title, titleLabelX, titleLabelY, 0xFF404040);
        canvas.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040);
        canvas.text(font, Component.translatable("screen.tristorage.insert_chests"),
                8, 21, 0xFF404040);
        canvas.text(font,
                Component.translatable("screen.tristorage.chests",
                        menu.installedChests(), menu.maxChests()),
                8, 68, 0xFF404040);
        canvas.text(font,
                Component.translatable("screen.tristorage.types",
                        menu.storedTypes(), menu.typeCapacity()),
                8, 80, 0xFF404040);
        canvas.text(font,
                Component.translatable("screen.tristorage.items",
                        menu.totalItems(), menu.itemCapacity()),
                8, 92, 0xFF404040);
    }
}
