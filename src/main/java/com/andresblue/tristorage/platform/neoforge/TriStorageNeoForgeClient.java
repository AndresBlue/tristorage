package com.andresblue.tristorage.platform.neoforge;

//? if neoforge {
/*import com.andresblue.tristorage.TriStorageClient;
import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.client.ConvergingPortalParticle;
import com.andresblue.tristorage.client.LinkerBlockEntityRenderer;
import com.andresblue.tristorage.platform.ClientPlatform;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

final class TriStorageNeoForgeClient {
    private TriStorageNeoForgeClient() {
    }

    static void initialize(IEventBus modBus) {
        ClientPlatform.install(new NeoForgeClientPlatformHooks());
        TriStorageClient.initialize();
        modBus.addListener(EventPriority.NORMAL, false, RegisterMenuScreensEvent.class,
                event -> TriStorageClient.registerScreens(event::register));
        modBus.addListener(EventPriority.NORMAL, false,
                EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(TriStorageMod.LINKER_BLOCK_ENTITY,
                        LinkerBlockEntityRenderer::new));
        modBus.addListener(EventPriority.NORMAL, false, RegisterParticleProvidersEvent.class,
                event -> {
                    event.registerSpriteSet(TriStorageMod.CONVERGING_PORTAL_PARTICLE,
                            ConvergingPortalParticle.Factory::new);
                    event.registerSpriteSet(TriStorageMod.CONVERGING_END_PARTICLE,
                            ConvergingPortalParticle.EndFactory::new);
                });
    }
}
*///?}
