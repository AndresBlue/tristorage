package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.TerminalScreenHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

public final class TerminalScreen extends HandledScreen<TerminalScreenHandler> {
    private ButtonWidget previousButton;
    private ButtonWidget nextButton;

    public TerminalScreen(TerminalScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 256;
        titleX = 8;
        titleY = 5;
        playerInventoryTitleX = 8;
        playerInventoryTitleY = 163;
    }

    @Override
    protected void init() {
        super.init();
        previousButton = addDrawableChild(new ButtonWidget(
                x + 7, y + 142, 20, 16, new LiteralText("<"),
                button -> clickPageButton(0)));
        nextButton = addDrawableChild(new ButtonWidget(
                x + 149, y + 142, 20, 16, new LiteralText(">"),
                button -> clickPageButton(1)));
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        previousButton.active = handler.page() > 0;
        nextButton.active = handler.page() + 1 < handler.pageCount();
    }

    private void clickPageButton(int id) {
        if (client != null && client.interactionManager != null) {
            client.interactionManager.clickButton(handler.syncId, id);
        }
    }

    @Override
    protected void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        fill(matrices, x, y, x + backgroundWidth, y + backgroundHeight, 0xFF20272D);
        fill(matrices, x + 4, y + 14, x + 172, y + 124, 0xFF101619);
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                int slotX = x + 7 + column * 18;
                int slotY = y + 17 + row * 18;
                fill(matrices, slotX, slotY, slotX + 18, slotY + 18, 0xFF4A5961);
                fill(matrices, slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF151C20);
            }
        }
        fill(matrices, x + 7, y + 173, x + 169, y + 251, 0xFF171C20);
    }

    @Override
    protected void drawForeground(MatrixStack matrices, int mouseX, int mouseY) {
        textRenderer.draw(matrices, title, titleX, titleY, 0xD8E7EC);
        textRenderer.draw(matrices, playerInventoryTitle,
                playerInventoryTitleX, playerInventoryTitleY, 0xAFC5CC);
        String pageText = (handler.page() + 1) + " / " + handler.pageCount();
        drawCenteredText(matrices, textRenderer, pageText, backgroundWidth / 2, 146, 0xBDECF2);
        textRenderer.draw(matrices,
                new TranslatableText("screen.tristorage.summary",
                        handler.storedTypes(), handler.totalItems()),
                8, 130, 0xAFC5CC);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        drawStoredCounts(matrices);
        drawStoredItemTooltip(matrices, mouseX, mouseY);
    }

    private void drawStoredCounts(MatrixStack matrices) {
        matrices.push();
        matrices.translate(0.0, 0.0, 400.0);
        for (int index = 0; index < TerminalScreenHandler.PAGE_SIZE; index++) {
            long count = handler.displayCount(index);
            if (count <= 1) {
                continue;
            }
            String label = compact(count);
            Slot slot = handler.slots.get(index);
            int drawX = x + slot.x + 17 - textRenderer.getWidth(label);
            int drawY = y + slot.y + 9;
            textRenderer.drawWithShadow(matrices, label, drawX, drawY, 0xFFFFFF);
        }
        matrices.pop();
    }

    private void drawStoredItemTooltip(MatrixStack matrices, int mouseX, int mouseY) {
        if (!handler.getCursorStack().isEmpty()) {
            return;
        }
        int relativeX = mouseX - x;
        int relativeY = mouseY - y;
        for (int index = 0; index < TerminalScreenHandler.PAGE_SIZE; index++) {
            Slot slot = handler.slots.get(index);
            if (relativeX >= slot.x && relativeX < slot.x + 16
                    && relativeY >= slot.y && relativeY < slot.y + 16
                    && slot.hasStack()) {
                ItemStack stack = slot.getStack();
                renderTooltip(matrices, stack, mouseX, mouseY);
                return;
            }
        }
    }

    private static String compact(long count) {
        if (count >= 1_000_000) {
            return (count / 1_000_000) + "m";
        }
        if (count >= 1_000) {
            return (count / 1_000) + "k";
        }
        return Long.toString(count);
    }
}
