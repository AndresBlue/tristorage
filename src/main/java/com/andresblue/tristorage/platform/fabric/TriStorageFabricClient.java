package com.andresblue.tristorage.platform.fabric;

//? if fabric {
import com.andresblue.tristorage.TriStorageClient;
import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.client.ConvergingPortalParticle;
import com.andresblue.tristorage.client.LinkerBlockEntityRenderer;
import com.andresblue.tristorage.platform.ClientPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
//? if >=1.20 {
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
*///?}

public final class TriStorageFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlatform.install(new FabricClientPlatformHooks());
        TriStorageClient.initialize();
        //? if >=1.20 {
        BlockEntityRenderers.register(TriStorageMod.LINKER_BLOCK_ENTITY,
                LinkerBlockEntityRenderer::new);
        //?} else {
        /*// Vanilla keeps the method private; Fabric widens it from a later version on.
        BlockEntityRendererRegistry.register(TriStorageMod.LINKER_BLOCK_ENTITY,
                LinkerBlockEntityRenderer::new);
        *///?}
        ParticleFactoryRegistry.getInstance().register(
                TriStorageMod.CONVERGING_PORTAL_PARTICLE, ConvergingPortalParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(
                TriStorageMod.CONVERGING_END_PARTICLE, ConvergingPortalParticle.EndFactory::new);
        TriStorageClient.registerScreens(MenuScreens::register);
    }
}
//?}
