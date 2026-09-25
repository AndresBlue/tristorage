package com.andresblue.tristorage;

import com.andresblue.tristorage.block.CraftingTerminalBlock;
import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.block.StorageCoreBlock;
import com.andresblue.tristorage.block.TerminalBlock;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.item.DimensionalAntennaItem;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.item.StorageCoreBlockItem;
import com.andresblue.tristorage.item.TerminalBlockItem;
import com.andresblue.tristorage.screen.CoreScreenHandler;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.screen.LinkerScreenHandler;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.recipe.CleanTerminalUpgradeRecipe;
import com.andresblue.tristorage.recipe.CoreUpgradeRecipe;
import com.andresblue.tristorage.network.TerminalPackets;
import com.andresblue.tristorage.storage.RemoteAccessManager;
import com.andresblue.tristorage.storage.StorageTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(TriStorageMod.MOD_ID)
public final class TriStorageMod {
    public static final String MOD_ID = "tristorage";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MOD_ID);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, CoreUpgradeRecipe.Serializer> CORE_UPGRADE_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register("core_upgrade", CoreUpgradeRecipe.Serializer::new);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, CleanTerminalUpgradeRecipe.Serializer> CLEAN_TERMINAL_UPGRADE_RECIPE_SERIALIZER = RECIPE_SERIALIZERS.register("clean_terminal_upgrade", CleanTerminalUpgradeRecipe.Serializer::new);

    public static final DeferredBlock<StorageCoreBlock> IRON_CORE = BLOCKS.register("iron_storage_core", () -> new StorageCoreBlock(StorageTier.IRON, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f)));
    public static final DeferredBlock<StorageCoreBlock> DIAMOND_CORE = BLOCKS.register("diamond_storage_core", () -> new StorageCoreBlock(StorageTier.DIAMOND, BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_BLOCK).strength(3.5f)));
    public static final DeferredBlock<StorageCoreBlock> BLAZE_CORE = BLOCKS.register("blaze_storage_core", () -> new StorageCoreBlock(StorageTier.BLAZE, BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(4.5f, 50.0f).lightLevel(s -> 4)));
    public static final DeferredBlock<StorageCoreBlock> COSMIC_CORE = BLOCKS.register("cosmic_storage_core", () -> new StorageCoreBlock(StorageTier.COSMIC, BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(5.0f, 1200.0f).lightLevel(s -> 7)));
    public static final DeferredBlock<TerminalBlock> TERMINAL = BLOCKS.register("storage_terminal", () -> new TerminalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f).lightLevel(s -> 5)));
    public static final DeferredBlock<CraftingTerminalBlock> CRAFTING_TERMINAL = BLOCKS.register("crafting_terminal", () -> new CraftingTerminalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f).lightLevel(s -> 7)));
    public static final DeferredBlock<LinkerBlock> LINKER = BLOCKS.register("storage_linker", () -> new LinkerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).strength(3.0f).lightLevel(s -> 3)));
    public static final DeferredItem<RemoteTabletItem> REMOTE_TABLET = ITEMS.register("remote_tablet", () -> new RemoteTabletItem(new Item.Properties()));
    public static final DeferredItem<DimensionalAntennaItem> DIMENSIONAL_ANTENNA = ITEMS.register("dimensional_antenna", () -> new DimensionalAntennaItem(new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageCoreBlockEntity>> STORAGE_CORE_BLOCK_ENTITY = BLOCK_ENTITIES.register("storage_core", () -> BlockEntityType.Builder.of(StorageCoreBlockEntity::new, IRON_CORE.get(), DIAMOND_CORE.get(), BLAZE_CORE.get(), COSMIC_CORE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LinkerBlockEntity>> LINKER_BLOCK_ENTITY = BLOCK_ENTITIES.register("storage_linker", () -> BlockEntityType.Builder.of(LinkerBlockEntity::new, LINKER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<CoreScreenHandler>> CORE_SCREEN_HANDLER = MENUS.register("storage_core", () -> new MenuType<>(CoreScreenHandler::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<TerminalScreenHandler>> TERMINAL_SCREEN_HANDLER = MENUS.register("storage_terminal", () -> new MenuType<>(TerminalScreenHandler::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<CraftingTerminalScreenHandler>> CRAFTING_TERMINAL_SCREEN_HANDLER = MENUS.register("crafting_terminal", () -> new MenuType<>(CraftingTerminalScreenHandler::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<LinkerScreenHandler>> LINKER_SCREEN_HANDLER = MENUS.register("storage_linker", () -> new MenuType<>(LinkerScreenHandler::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> CONVERGING_PORTAL_PARTICLE = PARTICLES.register("converging_portal", () -> new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> CONVERGING_END_PARTICLE = PARTICLES.register("converging_end", () -> new SimpleParticleType(false));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TRISTORAGE_TAB = TABS.register("tristorage", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.tristorage")).icon(() -> new ItemStack(TERMINAL.get())).displayItems((params, output) -> {
        output.accept(IRON_CORE.get()); output.accept(DIAMOND_CORE.get()); output.accept(BLAZE_CORE.get()); output.accept(COSMIC_CORE.get());
        output.accept(TERMINAL.get()); output.accept(CRAFTING_TERMINAL.get()); output.accept(LINKER.get()); output.accept(REMOTE_TABLET.get()); output.accept(DIMENSIONAL_ANTENNA.get());
    }).build());

    public TriStorageMod(IEventBus modBus) {
        registerBlockItem("iron_storage_core", IRON_CORE, true); registerBlockItem("diamond_storage_core", DIAMOND_CORE, true);
        registerBlockItem("blaze_storage_core", BLAZE_CORE, true); registerBlockItem("cosmic_storage_core", COSMIC_CORE, true);
        registerBlockItem("storage_terminal", TERMINAL, false); registerBlockItem("crafting_terminal", CRAFTING_TERMINAL, false); registerBlockItem("storage_linker", LINKER, false);
        BLOCKS.register(modBus); ITEMS.register(modBus); BLOCK_ENTITIES.register(modBus); MENUS.register(modBus); TABS.register(modBus); PARTICLES.register(modBus); RECIPE_SERIALIZERS.register(modBus);
        modBus.addListener(TerminalPackets::register);
        RemoteAccessManager.initialize();
        if (FMLEnvironment.dist == Dist.CLIENT) TriStorageClient.register(modBus);
    }
    private static void registerBlockItem(String name, DeferredBlock<? extends net.minecraft.world.level.block.Block> block, boolean core) {
        ITEMS.register(name, () -> core ? new StorageCoreBlockItem(block.get(), new Item.Properties()) : (name.equals("storage_terminal") ? new TerminalBlockItem(block.get(), new Item.Properties()) : new BlockItem(block.get(), new Item.Properties())));
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
