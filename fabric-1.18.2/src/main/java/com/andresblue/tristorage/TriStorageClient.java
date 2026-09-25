package com.andresblue.tristorage;

import com.andresblue.tristorage.client.CoreScreen;
import com.andresblue.tristorage.client.TerminalScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public final class TriStorageClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(TriStorageMod.CORE_SCREEN_HANDLER, CoreScreen::new);
        HandledScreens.register(TriStorageMod.TERMINAL_SCREEN_HANDLER, TerminalScreen::new);
    }
}
