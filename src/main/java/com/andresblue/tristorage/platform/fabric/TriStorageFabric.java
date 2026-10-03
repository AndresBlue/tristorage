package com.andresblue.tristorage.platform.fabric;

//? if fabric {
import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.platform.Platform;
import com.andresblue.tristorage.platform.Registrar;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Registry;
import com.andresblue.tristorage.version.GameRegistries;
//? if >=1.20 {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

public final class TriStorageFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platform.install(new FabricPlatformHooks());
        TriStorageMod.registerParticles(registrar(GameRegistries.PARTICLE_TYPE));
        TriStorageMod.registerBlocks(registrar(GameRegistries.BLOCK));
        //? if <1.20 {
        /*TriStorageMod.createCreativeTab();
        *///?}
        TriStorageMod.registerItems(registrar(GameRegistries.ITEM));
        TriStorageMod.registerRecipeSerializers(registrar(GameRegistries.RECIPE_SERIALIZER));
        TriStorageMod.registerBlockEntities(registrar(GameRegistries.BLOCK_ENTITY_TYPE));
        TriStorageMod.registerMenus(registrar(GameRegistries.MENU));
        //? if >=1.21.5 {
        /*TriStorageMod.registerTicketTypes(registrar(BuiltInRegistries.TICKET_TYPE));
        *///?}
        //? if >=1.21.11 {
        /*TriStorageMod.registerDataComponents(registrar(BuiltInRegistries.DATA_COMPONENT_TYPE));
        *///?}
        //? if >=1.20 {
        TriStorageMod.registerCreativeTabs(registrar(BuiltInRegistries.CREATIVE_MODE_TAB));
        //?}
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
