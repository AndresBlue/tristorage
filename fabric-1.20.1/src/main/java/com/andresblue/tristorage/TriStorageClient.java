package com.andresblue.tristorage;

import com.andresblue.tristorage.client.CoreScreen;
import com.andresblue.tristorage.client.CraftingTerminalScreen;
import com.andresblue.tristorage.client.TerminalScreen;
import com.andresblue.tristorage.client.TerminalClientNetworking;
import com.andresblue.tristorage.client.TerminalClientConfig;
import com.andresblue.tristorage.client.LinkerScreen;
import com.andresblue.tristorage.client.LinkerBlockEntityRenderer;
import com.andresblue.tristorage.client.ConvergingPortalParticle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class TriStorageClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TerminalClientConfig.initialize();
        TerminalClientNetworking.initialize();
        BlockEntityRendererFactories.register(TriStorageMod.LINKER_BLOCK_ENTITY,
                LinkerBlockEntityRenderer::new);
        ParticleFactoryRegistry.getInstance().register(
                TriStorageMod.CONVERGING_PORTAL_PARTICLE,
                ConvergingPortalParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(
                TriStorageMod.CONVERGING_END_PARTICLE,
                ConvergingPortalParticle.EndFactory::new);
        HandledScreens.register(TriStorageMod.CORE_SCREEN_HANDLER, CoreScreen::new);
        HandledScreens.register(TriStorageMod.TERMINAL_SCREEN_HANDLER, TerminalScreen::new);
        HandledScreens.register(TriStorageMod.CRAFTING_TERMINAL_SCREEN_HANDLER,
                CraftingTerminalScreen::new);
        HandledScreens.register(TriStorageMod.LINKER_SCREEN_HANDLER, LinkerScreen::new);
    }
}
