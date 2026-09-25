package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.CoreScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public final class CoreScreen extends HandledScreen<CoreScreenHandler> {
    private static final int TEXT_COLOR = 0xFF404040;
    private ButtonWidget withdrawButton;

    public CoreScreen(CoreScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 204;
        titleX = 8;
        titleY = 7;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = 111;
    }

    @Override
    protected void init() {
        super.init();
        withdrawButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("button.tristorage.withdraw_chests"), ignored -> {
                            if (client != null && client.interactionManager != null) {
                                client.interactionManager.clickButton(handler.syncId, 0);
                            }
                        })
                .dimensions(x + 108, y + 34, 58, 16).build());
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        withdrawButton.active = handler.installedChests() > 0;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        drawVanillaPanel(context, x, y, x + backgroundWidth, y + backgroundHeight);
        context.fill(x + 5, y + 17, x + 171, y + 107, -4342339);
        drawSlot(context, x + 79, y + 34);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(context, x + 7 + column * 18, y + 121 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(context, x + 7 + column * 18, y + 179);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, TEXT_COLOR, false);
        context.drawText(textRenderer, playerInventoryTitle,
                playerInventoryTitleX, playerInventoryTitleY, TEXT_COLOR, false);
        context.drawText(textRenderer, Text.translatable("screen.tristorage.insert_chests"),
                8, 21, TEXT_COLOR, false);
        context.drawText(textRenderer, Text.translatable("screen.tristorage.chests",
                handler.installedChests(), handler.maxChests()), 8, 68, TEXT_COLOR, false);
        context.drawText(textRenderer, Text.translatable("screen.tristorage.types",
                handler.storedTypes(), handler.typeCapacity()), 8, 80, TEXT_COLOR, false);
        context.drawText(textRenderer, Text.translatable("screen.tristorage.items",
                handler.totalItems(), handler.itemCapacity()), 8, 92, TEXT_COLOR, false);
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
