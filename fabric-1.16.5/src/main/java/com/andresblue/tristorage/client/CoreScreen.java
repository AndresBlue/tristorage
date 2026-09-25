package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.CoreScreenHandler;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
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
        withdrawButton = addButton(new ButtonWidget(
                        x + 104, y + 31, 62, 20,
                        new TranslatableText("button.tristorage.withdraw_chests"),
                        button -> {
                            if (client != null && client.interactionManager != null) {
                                client.interactionManager.clickButton(handler.syncId, 0);
                            }
                        }));
    }

    @Override
    public void tick() {
        super.tick();
        withdrawButton.active = handler.installedChests() > 0;
    }

    @Override
    protected void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        fill(matrices, x, y, x + backgroundWidth, y + backgroundHeight, 0xFF20272D);
        fill(matrices, x + 4, y + 4, x + backgroundWidth - 4, y + 106, 0xFF303B43);
        fill(matrices, x + 77, y + 32, x + 95, y + 50, 0xFF12171A);
        fill(matrices, x + 78, y + 33, x + 94, y + 49, 0xFF56636B);
        fill(matrices, x + 7, y + 121, x + 169, y + 199, 0xFF171C20);
    }

    @Override
    protected void drawForeground(MatrixStack matrices, int mouseX, int mouseY) {
        textRenderer.draw(matrices, title, titleX, titleY, 0xD8E7EC);
        textRenderer.draw(matrices, playerInventory.getName(),
                playerInventoryTitleX, playerInventoryTitleY, 0xAFC5CC);
        textRenderer.draw(matrices,
                new TranslatableText("screen.tristorage.insert_chests").formatted(Formatting.AQUA),
                8, 21, 0xFFFFFF);
        textRenderer.draw(matrices,
                new TranslatableText("screen.tristorage.chests",
                        handler.installedChests(), handler.maxChests()),
                8, 68, 0xD8E7EC);
        textRenderer.draw(matrices,
                new TranslatableText("screen.tristorage.types",
                        handler.storedTypes(), handler.typeCapacity()),
                8, 80, 0xB8C6CC);
        textRenderer.draw(matrices,
                new TranslatableText("screen.tristorage.items",
                        handler.totalItems(), handler.itemCapacity()),
                8, 92, 0xB8C6CC);
    }
}
