package com.andresblue.tristorage.client;

import net.minecraft.client.Minecraft;

/**
 * Prevents inventory helper mods from turning the same wheel event used for
 * terminal navigation into a slot transfer. The guard only lives for the
 * synchronous dispatch of MouseHandler.onScroll.
 */
public final class TerminalScrollGuard {
    private static int depth;

    private TerminalScrollGuard() {
    }

    public static void begin() {
        if (Minecraft.getInstance().screen instanceof TerminalScreenMarker) {
            depth++;
        }
    }

    public static void end() {
        if (depth > 0) {
            depth--;
        }
    }

    public static boolean blocksInventoryClicks() {
        return depth > 0
                && Minecraft.getInstance().screen instanceof TerminalScreenMarker;
    }
}
