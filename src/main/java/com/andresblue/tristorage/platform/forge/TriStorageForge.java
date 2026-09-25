package com.andresblue.tristorage.platform.forge;

//? if forge {
/*import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.platform.Platform;
import com.andresblue.tristorage.platform.Registrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

@Mod(TriStorageMod.MOD_ID)
public final class TriStorageForge {
    public TriStorageForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Platform.install(new ForgePlatformHooks());
        modBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class,
                TriStorageForge::register);
        TriStorageMod.initialize();
        // Runs last so another mod's refusal is known before a Core turns portable.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                BlockEvent.BreakEvent.class, TriStorageForge::onBlockBreak);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TriStorageForgeClient.initialize(modBus);
        }
    }

    // Forge only accepts registry entries while firing RegisterEvent for that registry.
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

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
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
