package com.andresblue.tristorage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=1.19.3 {
import net.minecraft.client.gui.components.Tooltip;
//?} else {
/*import java.util.Map;
import java.util.WeakHashMap;
*///?}

/** Widget and screen calls that changed between Minecraft versions. */
final class Widgets {
    //? if <1.19.3 {
    /*private static final Map<AbstractWidget, Component> HINTS = new WeakHashMap<>();
    *///?}

    private Widgets() {
    }

    /** The screen being shown, or null. It moved from Minecraft to its Gui in 26.2. */
    static Screen currentScreen(Minecraft minecraft) {
        //? if >=26.2 {
        /*return minecraft.gui.screen();
        *///?} else {
        return minecraft.screen;
        //?}
    }

    static Button button(int x, int y, int width, int height, Component message,
                         Button.OnPress onPress) {
        //? if >=1.19.3 {
        return Button.builder(message, onPress).bounds(x, y, width, height).build();
        //?} else {
        /*return new Button(x, y, width, height, message, onPress);
        *///?}
    }

    static int x(AbstractWidget widget) {
        //? if >=1.19.3 {
        return widget.getX();
        //?} else {
        /*return widget.x;
        *///?}
    }

    static int y(AbstractWidget widget) {
        //? if >=1.19.3 {
        return widget.getY();
        //?} else {
        /*return widget.y;
        *///?}
    }

    static void setFocused(EditBox field, boolean focused) {
        //? if >=1.19.4 {
        field.setFocused(focused);
        //?} else {
        /*field.setFocus(focused);
        *///?}
    }

    /** Shows {@code hint} while the pointer rests on the widget. */
    static void hint(AbstractWidget widget, Component hint) {
        //? if >=1.19.3 {
        widget.setTooltip(Tooltip.create(hint));
        //?} else {
        /*HINTS.put(widget, hint);
        *///?}
    }

    /**
     * Draws the hint of the widget under the pointer. Only versions before
     * 1.19.3 need the call; later ones draw widget tooltips themselves.
     */
    static void drawHints(Canvas canvas, Screen screen, Iterable<? extends AbstractWidget> widgets,
                          int mouseX, int mouseY) {
        //? if <1.19.3 {
        /*for (AbstractWidget widget : widgets) {
            Component hint = HINTS.get(widget);
            if (hint != null && widget.visible && widget.isMouseOver(mouseX, mouseY)) {
                canvas.tooltip(screen, hint, mouseX, mouseY);
                return;
            }
        }
        *///?}
    }
}
