package com.andresblue.tristorage.platform.forge;

//? if forge {
/*import com.andresblue.tristorage.TriStorageClient;
import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.client.ConvergingPortalParticle;
import com.andresblue.tristorage.client.LinkerBlockEntityRenderer;
import com.andresblue.tristorage.platform.ClientPlatform;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

final class TriStorageForgeClient {
    private TriStorageForgeClient() {
    }

    static void initialize(IEventBus modBus) {
        ClientPlatform.install(new ForgeClientPlatformHooks());
        TriStorageClient.initialize();
        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class,
                event -> event.enqueueWork(TriStorageClient::registerScreens));
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
