package com.andresblue.tristorage.platform.neoforge;

//? if neoforge {
/*import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.platform.Platform;
import com.andresblue.tristorage.platform.Registrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
//? if >=26.1 {
/^import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
^///?} else {
import net.neoforged.neoforge.event.level.BlockEvent;
//?}
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(TriStorageMod.MOD_ID)
public final class TriStorageNeoForge {
    public TriStorageNeoForge(IEventBus modBus) {
        Platform.install(new NeoForgePlatformHooks(modBus));
        modBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class,
                TriStorageNeoForge::register);
        TriStorageMod.initialize();
        // Runs last so another mod's refusal is known before a Core turns portable.
        //? if >=26.1 {
        /^NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                BreakBlockEvent.class, TriStorageNeoForge::onBlockBreak);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
        ^///?} else {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                BlockEvent.BreakEvent.class, TriStorageNeoForge::onBlockBreak);
        if (FMLEnvironment.dist == Dist.CLIENT) {
        //?}
            TriStorageNeoForgeClient.initialize(modBus);
        }
    }

    // NeoForge only accepts registry entries while firing RegisterEvent for that registry.
    private static void register(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE,
                helper -> TriStorageMod.registerParticles(registrar(helper)));
        event.register(Registries.BLOCK,
                helper -> TriStorageMod.registerBlocks(registrar(helper)));
        event.register(Registries.ITEM,
                helper -> TriStorageMod.registerItems(registrar(helper)));
        event.register(Registries.RECIPE_SERIALIZER,
                helper -> TriStorageMod.registerRecipeSerializers(registrar(helper)));
        event.register(Registries.BLOCK_ENTITY_TYPE,
                helper -> TriStorageMod.registerBlockEntities(registrar(helper)));
        event.register(Registries.MENU,
                helper -> TriStorageMod.registerMenus(registrar(helper)));
        //? if >=1.21.5 {
        event.register(Registries.TICKET_TYPE,
                helper -> TriStorageMod.registerTicketTypes(registrar(helper)));
        //?}
        //? if >=1.21.11 {
        event.register(Registries.DATA_COMPONENT_TYPE,
                helper -> TriStorageMod.registerDataComponents(registrar(helper)));
        //?}
        event.register(Registries.CREATIVE_MODE_TAB,
                helper -> TriStorageMod.registerCreativeTabs(registrar(helper)));
    }

    private static <T> Registrar<T> registrar(RegisterEvent.RegisterHelper<T> helper) {
        return new Registrar<>() {
            @Override
            public <V extends T> V register(String path, V value) {
                helper.register(TriStorageMod.id(path), value);
                return value;
            }
        };
    }

    //? if >=26.1 {
    /^private static void onBlockBreak(BreakBlockEvent event) {
    ^///?} else {
    private static void onBlockBreak(BlockEvent.BreakEvent event) {
    //?}
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if (!TriStorageMod.beforeBlockBreak(level, event.getPlayer(), event.getState(),
                blockEntity)) {
            event.setCanceled(true);
        }
    }
}
*///?}
