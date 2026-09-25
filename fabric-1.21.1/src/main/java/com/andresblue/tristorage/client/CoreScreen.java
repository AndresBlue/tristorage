package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.CoreScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class CoreScreen extends HandledScreen<CoreScreenHandler> {
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
                        Text.translatable("button.tristorage.withdraw_chests"),
                        button -> {
                            if (client != null && client.interactionManager != null) {
                                client.interactionManager.clickButton(handler.syncId, 0);
                            }
                        })
                .dimensions(x + 104, y + 31, 62, 20)
                .build());
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        withdrawButton.active = handler.installedChests() > 0;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF20272D);
        context.fill(x + 4, y + 4, x + backgroundWidth - 4, y + 106, 0xFF303B43);
        context.fill(x + 77, y + 32, x + 95, y + 50, 0xFF12171A);
        context.fill(x + 78, y + 33, x + 94, y + 49, 0xFF56636B);
        context.fill(x + 7, y + 121, x + 169, y + 199, 0xFF171C20);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0xFFD8E7EC, false);
        context.drawText(textRenderer, playerInventoryTitle,
                playerInventoryTitleX, playerInventoryTitleY, 0xFFAFC5CC, false);
        context.drawText(textRenderer,
                Text.translatable("screen.tristorage.insert_chests").formatted(Formatting.AQUA),
                8, 21, 0xFFFFFFFF, false);
        context.drawText(textRenderer,
                Text.translatable("screen.tristorage.chests",
                        handler.installedChests(), handler.maxChests()),
                8, 68, 0xFFD8E7EC, false);
        context.drawText(textRenderer,
                Text.translatable("screen.tristorage.types",
                        handler.storedTypes(), handler.typeCapacity()),
                8, 80, 0xFFB8C6CC, false);
        context.drawText(textRenderer,
                Text.translatable("screen.tristorage.items",
                        handler.totalItems(), handler.itemCapacity()),
                8, 92, 0xFFB8C6CC, false);
    }
}
