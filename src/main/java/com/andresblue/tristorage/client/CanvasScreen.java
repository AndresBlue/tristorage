package com.andresblue.tristorage.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}

/**
 * A container screen that draws through {@link Canvas}, so its subclasses are
 * the same code on every Minecraft version.
 */
abstract class CanvasScreen<H extends AbstractContainerMenu> extends AbstractContainerScreen<H> {
    protected CanvasScreen(H menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** The screen's background, in window coordinates. */
    protected abstract void drawBackground(Canvas canvas);

    /** Labels, relative to the screen's top left corner. */
    protected abstract void drawLabels(Canvas canvas, int mouseX, int mouseY);

    /**
     * Draws above the slots and widgets, in window coordinates. Returns
     * whether the hovered slot should still show its tooltip.
     */
    protected boolean drawForeground(Canvas canvas, int mouseX, int mouseY, float delta) {
        return true;
    }

    @Override
    //? if >=1.20 {
    protected final void renderBg(GuiGraphics target, float delta, int mouseX, int mouseY) {
    //?} else {
    /*protected final void renderBg(PoseStack target, float delta, int mouseX, int mouseY) {
    *///?}
        drawBackground(new Canvas(target));
    }

    @Override
    //? if >=1.20 {
    protected final void renderLabels(GuiGraphics target, int mouseX, int mouseY) {
    //?} else {
    /*protected final void renderLabels(PoseStack target, int mouseX, int mouseY) {
    *///?}
        drawLabels(new Canvas(target), mouseX, mouseY);
    }

    @Override
    //? if >=1.20 {
    public final void render(GuiGraphics target, int mouseX, int mouseY, float delta) {
    //?} else {
    /*public final void render(PoseStack target, int mouseX, int mouseY, float delta) {
    *///?}
        // From 1.20.2 on, super.render draws the background itself.
        //? if <1.20.2 {
        renderBackground(target);
        //?}
        super.render(target, mouseX, mouseY, delta);
        if (drawForeground(new Canvas(target), mouseX, mouseY, delta)) {
            renderTooltip(target, mouseX, mouseY);
        }
    }
}
