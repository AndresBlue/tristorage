package com.andresblue.tristorage.client;

import com.andresblue.tristorage.screen.TerminalScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

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
        previousButton = addDrawableChild(ButtonWidget.builder(Text.literal("<"),
                        button -> clickPageButton(0))
                .dimensions(x + 7, y + 142, 20, 16)
                .build());
        nextButton = addDrawableChild(ButtonWidget.builder(Text.literal(">"),
                        button -> clickPageButton(1))
                .dimensions(x + 149, y + 142, 20, 16)
                .build());
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
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF20272D);
        context.fill(x + 4, y + 14, x + 172, y + 124, 0xFF101619);
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                int slotX = x + 7 + column * 18;
                int slotY = y + 17 + row * 18;
                context.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF4A5961);
                context.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF151C20);
            }
        }
        context.fill(x + 7, y + 173, x + 169, y + 251, 0xFF171C20);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0xFFD8E7EC, false);
        context.drawText(textRenderer, playerInventoryTitle,
                playerInventoryTitleX, playerInventoryTitleY, 0xFFAFC5CC, false);
        String pageText = (handler.page() + 1) + " / " + handler.pageCount();
        context.drawCenteredTextWithShadow(
                textRenderer, pageText, backgroundWidth / 2, 146, 0xFFBDECF2);
        context.drawText(textRenderer,
                Text.translatable("screen.tristorage.summary",
                        handler.storedTypes(), handler.totalItems()),
                8, 130, 0xFFAFC5CC, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawStoredCounts(context);
        drawStoredItemTooltip(context, mouseX, mouseY);
    }

    private void drawStoredCounts(DrawContext context) {
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 400.0f);
        for (int index = 0; index < TerminalScreenHandler.PAGE_SIZE; index++) {
            long count = handler.displayCount(index);
            if (count <= 1) {
                continue;
            }
            String label = compact(count);
            Slot slot = handler.slots.get(index);
            int drawX = x + slot.x + 17 - textRenderer.getWidth(label);
            int drawY = y + slot.y + 9;
            context.drawText(textRenderer, label, drawX, drawY, 0xFFFFFFFF, true);
        }
        context.getMatrices().pop();
    }

    private void drawStoredItemTooltip(DrawContext context, int mouseX, int mouseY) {
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
                context.drawItemTooltip(textRenderer, stack, mouseX, mouseY);
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
