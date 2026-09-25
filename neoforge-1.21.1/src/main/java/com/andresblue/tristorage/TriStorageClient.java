package com.andresblue.tristorage;

import com.andresblue.tristorage.client.CoreScreen;
import com.andresblue.tristorage.client.TerminalScreen;
import com.andresblue.tristorage.client.CraftingTerminalScreen;
import com.andresblue.tristorage.client.LinkerScreen;
import com.andresblue.tristorage.client.LinkerBlockEntityRenderer;
import com.andresblue.tristorage.client.ConvergingParticle;
import com.andresblue.tristorage.client.TerminalClientConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public final class TriStorageClient {
    private TriStorageClient() {}

    public static void register(IEventBus modBus) {
        TerminalClientConfig.initialize();
        modBus.addListener(TriStorageClient::registerScreens);
        modBus.addListener(TriStorageClient::registerRenderers);
        modBus.addListener(TriStorageClient::registerParticles);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(TriStorageMod.CORE_SCREEN_HANDLER.get(), CoreScreen::new);
        event.register(TriStorageMod.TERMINAL_SCREEN_HANDLER.get(), TerminalScreen::new);
        event.register(TriStorageMod.CRAFTING_TERMINAL_SCREEN_HANDLER.get(), CraftingTerminalScreen::new);
        event.register(TriStorageMod.LINKER_SCREEN_HANDLER.get(), LinkerScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(TriStorageMod.LINKER_BLOCK_ENTITY.get(), LinkerBlockEntityRenderer::new);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(TriStorageMod.CONVERGING_PORTAL_PARTICLE.get(), sprites -> new ConvergingParticle.Provider(sprites, false));
        event.registerSpriteSet(TriStorageMod.CONVERGING_END_PARTICLE.get(), sprites -> new ConvergingParticle.Provider(sprites, true));
    }
}
