package com.andresblue.tristorage;

import com.andresblue.tristorage.client.CoreScreen;
import com.andresblue.tristorage.client.CraftingTerminalScreen;
import com.andresblue.tristorage.client.TerminalScreen;
import com.andresblue.tristorage.client.TerminalClientNetworking;
import com.andresblue.tristorage.client.TerminalClientConfig;
import com.andresblue.tristorage.client.LinkerScreen;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Loader-independent client initialization. Block entity renderers and
 * particle providers are registered by each loader's client entrypoint.
 */
public final class TriStorageClient {
    private TriStorageClient() {
    }

    public static void initialize() {
        TerminalClientConfig.initialize();
        TerminalClientNetworking.initialize();
    }

    public static void registerScreens() {
        MenuScreens.register(TriStorageMod.CORE_SCREEN_HANDLER, CoreScreen::new);
        MenuScreens.register(TriStorageMod.TERMINAL_SCREEN_HANDLER, TerminalScreen::new);
        MenuScreens.register(TriStorageMod.CRAFTING_TERMINAL_SCREEN_HANDLER,
                CraftingTerminalScreen::new);
        MenuScreens.register(TriStorageMod.LINKER_SCREEN_HANDLER, LinkerScreen::new);
    }
}
