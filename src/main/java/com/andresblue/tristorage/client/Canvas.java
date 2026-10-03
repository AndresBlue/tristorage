package com.andresblue.tristorage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.andresblue.tristorage.version.Ids;
//? if >=1.21.9 {
/*import net.minecraft.network.chat.FontDescription;
*///?}
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} elif >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
*///?}
//? if >=1.21.6 {
/*import net.minecraft.client.renderer.RenderPipelines;
*///?}

/**
 * The GUI drawing TriStorage does, over the API of the running Minecraft
 * version: a bare {@code PoseStack} before 1.20, {@code GuiGraphics} since,
 * 2D matrices with deferred item rendering from 1.21.6, and the renamed
 * {@code GuiGraphicsExtractor} from 26.1.
 */
final class Canvas {
    /** Above item icons and their vanilla count labels. */
    private static final float LABEL_Z = 325.0f;
    private static final float OVERLAY_Z = 500.0f;

    // TriStorage draws its text in a font of its own that points at the default
    // one, so a resource pack can restyle these screens alone. Fonts cannot
    // refer to another font before 1.20; there the text stays in the default.
    //? if >=1.21.9 {
    /*private static final FontDescription GUI_FONT =
            new FontDescription.Resource(Ids.of("tristorage", "gui"));
    *///?} elif >=1.20 {
    private static final ResourceLocation GUI_FONT = Ids.of("tristorage", "gui");
    //?}

    //? if >=26.1 {
    /*private final GuiGraphicsExtractor target;

    Canvas(GuiGraphicsExtractor target) {
        this.target = target;
    }
    *///?} elif >=1.20 {
    private final GuiGraphics target;

    Canvas(GuiGraphics target) {
        this.target = target;
    }
    //?} else {
    /*private final PoseStack target;

    Canvas(PoseStack target) {
        this.target = target;
    }
    *///?}

    private static Component inGuiFont(Component text) {
        //? if >=1.20 {
        return text.copy().withStyle(style -> style.withFont(GUI_FONT));
        //?} else {
        /*return text;
        *///?}
    }

    /** The width of text as this canvas draws it. */
    int width(Font font, Component text) {
        return font.width(inGuiFont(text));
    }

    int width(Font font, String text) {
        return width(font, Component.literal(text));
    }

    /** Text without a shadow. The color needs its alpha byte: 1.21.6 draws nothing without. */
    void text(Font font, Component plain, int x, int y, int color) {
        Component text = inGuiFont(plain);
        //? if >=26.1 {
        /*target.text(font, text, x, y, color, false);
        *///?} elif >=1.20 {
        target.drawString(font, text, x, y, color, false);
        //?} else {
        /*font.draw(target, text, x, y, color);
        *///?}
    }

    void text(Font font, String text, int x, int y, int color) {
        text(font, Component.literal(text), x, y, color);
    }

    /**
     * White shadowed text ending at {@code right}, scaled around its top right
     * corner and drawn above item icons.
     */
    void countLabel(Font font, String plain, float right, float y, float scale) {
        Component label = inGuiFont(Component.literal(plain));
        int width = font.width(label);
        //? if >=26.1 {
        /*target.pose().pushMatrix();
        target.pose().translate(right, y);
        target.pose().scale(scale, scale);
        target.text(font, label, -width, 0, 0xFFFFFFFF, true);
        target.pose().popMatrix();
        *///?} elif >=1.21.6 {
        /*target.pose().pushMatrix();
        target.pose().translate(right, y);
        target.pose().scale(scale, scale);
        target.drawString(font, label, -width, 0, 0xFFFFFFFF, true);
        target.pose().popMatrix();
        *///?} elif >=1.20 {
        target.pose().pushPose();
        target.pose().translate(right, y, LABEL_Z);
        target.pose().scale(scale, scale, 1.0f);
        target.drawString(font, label, -width, 0, 0xFFFFFFFF, true);
        target.pose().popPose();
        //?} else {
        /*target.pushPose();
        target.translate(right, y, LABEL_Z);
        target.scale(scale, scale, 1.0f);
        font.drawShadow(target, label, -width, 0, 0xFFFFFFFF);
        target.popPose();
        *///?}
    }

    void fill(int left, int top, int right, int bottom, int color) {
        //? if >=1.20 {
        target.fill(left, top, right, bottom, color);
        //?} else {
        /*GuiComponent.fill(target, left, top, right, bottom, color);
        *///?}
    }

    /** Draws the top left {@code width} x {@code height} pixels of a 256x256 texture. */
    void texture(ResourceLocation texture, int x, int y, int width, int height) {
        //? if >=1.21.6 {
        /*target.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, 256, 256);
        *///?} elif >=1.20 {
        target.blit(texture, x, y, 0, 0, width, height);
        //?} else {
        /*RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(target, x, y, 0.0f, 0.0f, width, height, 256, 256);
        *///?}
    }

    void item(ItemStack stack, int x, int y) {
        //? if >=26.1 {
        /*target.item(stack, x, y);
        *///?} elif >=1.20 {
        target.renderItem(stack, x, y);
        //?} else {
        /*Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y);
        *///?}
    }

    void widget(AbstractWidget widget, int mouseX, int mouseY, float delta) {
        //? if >=26.1 {
        /*widget.extractRenderState(target, mouseX, mouseY, delta);
        *///?} else {
        widget.render(target, mouseX, mouseY, delta);
        //?}
    }

    //? if <1.19.3 {
    /*// Widgets carry their own tooltip since 1.19.3; before, the screen draws it.
    void tooltip(Screen screen, Component text, int mouseX, int mouseY) {
        screen.renderTooltip(target, text, mouseX, mouseY);
    }
    *///?}

    /** Starts drawing above everything drawn so far, slots and their items included. */
    void beginOverlay() {
        //? if >=1.21.6 {
        /*target.nextStratum();
        *///?} elif >=1.20 {
        target.pose().pushPose();
        target.pose().translate(0.0f, 0.0f, OVERLAY_Z);
        //?} else {
        /*target.pushPose();
        target.translate(0.0, 0.0, OVERLAY_Z);
        *///?}
    }

    void endOverlay() {
        //? if >=1.20 && <1.21.6 {
        target.pose().popPose();
        //?} elif <1.20 {
        /*target.popPose();
        *///?}
    }

    /** Sends what was drawn so far to the screen, on the versions that batch GUI drawing. */
    void flush() {
        //? if >=1.20 && <1.21.6 {
        target.flush();
        //?}
    }

    /**
     * A canvas for a single item icon. From 1.20 to 1.21.5 item renderers
     * receive the screen's own matrices, and some modded ones leave them
     * unbalanced: one such icon deformed every tab drawn after it and produced
     * an endless push/pop error stream. There the icon gets a throwaway canvas
     * with two guard frames that absorb stray pops; they are never balanced
     * because the canvas is dropped after {@link #flush()}. Other versions
     * draw items outside the screen's matrices and return this canvas.
     */
    Canvas isolated(Minecraft minecraft) {
        //? if >=1.20 && <1.21.6 {
        GuiGraphics isolated = new GuiGraphics(minecraft, target.bufferSource());
        isolated.pose().pushPose();
        isolated.pose().pushPose();
        return new Canvas(isolated);
        //?} else {
        /*return this;
        *///?}
    }

    /** A raised panel in the vanilla container style. */
    void panel(int left, int top, int right, int bottom) {
        fill(left, top, right, bottom, 0xFF373737);
        fill(left + 1, top + 1, right - 1, bottom - 1, 0xFFC6C6C6);
        fill(left + 1, top + 1, right - 1, top + 2, 0xFFFFFFFF);
        fill(left + 1, top + 1, left + 2, bottom - 1, 0xFFFFFFFF);
        fill(left + 1, bottom - 2, right - 1, bottom - 1, 0xFF555555);
        fill(right - 2, top + 1, right - 1, bottom - 1, 0xFF555555);
    }

    /** An 18x18 sunken slot in the vanilla container style. */
    void slotFrame(int left, int top) {
        fill(left, top, left + 18, top + 18, 0xFF8B8B8B);
        fill(left, top, left + 18, top + 1, 0xFF373737);
        fill(left, top, left + 1, top + 18, 0xFF373737);
        fill(left, top + 17, left + 18, top + 18, 0xFFFFFFFF);
        fill(left + 17, top, left + 18, top + 18, 0xFFFFFFFF);
    }
}
