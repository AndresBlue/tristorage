package com.andresblue.tristorage;

import com.andresblue.tristorage.client.CoreScreen;
import com.andresblue.tristorage.client.CraftingTerminalScreen;
import com.andresblue.tristorage.client.TerminalScreen;
import com.andresblue.tristorage.client.TerminalClientNetworking;
import com.andresblue.tristorage.client.TerminalClientConfig;
import com.andresblue.tristorage.client.LinkerScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

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

    /** Pairs each menu with its screen through the loader's own registration. */
    public static void registerScreens(ScreenRegistrar registrar) {
        registrar.register(TriStorageMod.CORE_SCREEN_HANDLER, CoreScreen::new);
        registrar.register(TriStorageMod.TERMINAL_SCREEN_HANDLER, TerminalScreen::new);
        registrar.register(TriStorageMod.CRAFTING_TERMINAL_SCREEN_HANDLER,
                CraftingTerminalScreen::new);
        registrar.register(TriStorageMod.LINKER_SCREEN_HANDLER, LinkerScreen::new);
    }

    /**
     * Vanilla's {@code MenuScreens.register}, or the loader event that stands
     * in for it where that method is private.
     */
    @FunctionalInterface
    public interface ScreenRegistrar {
        <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(
                MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> factory);
    }
}
