package com.andresblue.tristorage.client;

import net.minecraft.client.MinecraftClient;

/** Prevents inventory-tweak mods from translating terminal wheel events into slot clicks. */
public final class TerminalScrollGuard {
    private static int depth;

    private TerminalScrollGuard() {
    }

    public static void begin() {
        if (MinecraftClient.getInstance().currentScreen instanceof TerminalScreenMarker) {
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
                && MinecraftClient.getInstance().currentScreen instanceof TerminalScreenMarker;
    }
}
