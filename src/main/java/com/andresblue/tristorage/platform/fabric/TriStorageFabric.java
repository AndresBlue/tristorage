package com.andresblue.tristorage.platform.fabric;

//? if fabric {
import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.platform.Platform;
import com.andresblue.tristorage.platform.Registrar;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class TriStorageFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.install(new FabricPlatformHooks());
        TriStorageMod.registerParticles(registrar(BuiltInRegistries.PARTICLE_TYPE));
        TriStorageMod.registerBlocks(registrar(BuiltInRegistries.BLOCK));
        TriStorageMod.registerItems(registrar(BuiltInRegistries.ITEM));
        TriStorageMod.registerRecipeSerializers(registrar(BuiltInRegistries.RECIPE_SERIALIZER));
        TriStorageMod.registerBlockEntities(registrar(BuiltInRegistries.BLOCK_ENTITY_TYPE));
        TriStorageMod.registerMenus(registrar(BuiltInRegistries.MENU));
        TriStorageMod.registerCreativeTabs(registrar(BuiltInRegistries.CREATIVE_MODE_TAB));
        TriStorageMod.initialize();
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                TriStorageMod.beforeBlockBreak(level, player, state, blockEntity));
        PlayerBlockBreakEvents.CANCELED.register((level, player, pos, state, blockEntity) ->
                TriStorageMod.afterBlockBreakCanceled(blockEntity));
    }

    private static <T> Registrar<T> registrar(Registry<T> registry) {
        return new Registrar<>() {
            @Override
            public <V extends T> V register(String path, V value) {
                return Registry.register(registry, TriStorageMod.id(path), value);
            }
        };
    }
}
//?}
