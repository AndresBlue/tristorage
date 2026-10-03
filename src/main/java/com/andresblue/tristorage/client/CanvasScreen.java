package com.andresblue.tristorage.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} elif >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
*///?}

/**
 * A container screen that draws through {@link Canvas}, so its subclasses are
 * the same code on every Minecraft version.
 */
abstract class CanvasScreen<H extends AbstractContainerMenu> extends AbstractContainerScreen<H> {
    //? if >=26.1 {
    /*private float frameDelta;
    *///?}

    /** The size is the background's; since 26.1 it is fixed when the screen is created. */
    protected CanvasScreen(H menu, Inventory inventory, Component title, int width, int height) {
        //? if >=26.1 {
        /*super(menu, inventory, title, width, height);
        *///?} else {
        super(menu, inventory, title);
        imageWidth = width;
        imageHeight = height;
        //?}
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

    //? if >=26.1 {
    /*@Override
    public final void extractBackground(GuiGraphicsExtractor target, int mouseX, int mouseY,
                                        float delta) {
        super.extractBackground(target, mouseX, mouseY, delta);
        drawBackground(new Canvas(target));
    }

    @Override
    protected final void extractLabels(GuiGraphicsExtractor target, int mouseX, int mouseY) {
        drawLabels(new Canvas(target), mouseX, mouseY);
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor target, int mouseX, int mouseY,
                                         float delta) {
        frameDelta = delta;
        super.extractRenderState(target, mouseX, mouseY, delta);
    }

    // Since 26.1 the screen draws the slot tooltip itself, as its last step.
    @Override
    protected final void extractTooltip(GuiGraphicsExtractor target, int mouseX, int mouseY) {
        if (drawForeground(new Canvas(target), mouseX, mouseY, frameDelta)) {
            super.extractTooltip(target, mouseX, mouseY);
        }
    }
    *///?} else {
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
    //?}
}
