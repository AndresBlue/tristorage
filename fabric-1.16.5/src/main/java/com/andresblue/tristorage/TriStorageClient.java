package com.andresblue.tristorage;

import com.andresblue.tristorage.client.CoreScreen;
import com.andresblue.tristorage.client.TerminalScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;

public final class TriStorageClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenRegistry.register(TriStorageMod.CORE_SCREEN_HANDLER, CoreScreen::new);
        ScreenRegistry.register(TriStorageMod.TERMINAL_SCREEN_HANDLER, TerminalScreen::new);
    }
}
