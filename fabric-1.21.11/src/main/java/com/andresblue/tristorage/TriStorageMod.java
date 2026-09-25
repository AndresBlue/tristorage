package com.andresblue.tristorage;

import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.block.CraftingTerminalBlock;
import com.andresblue.tristorage.block.StorageCoreBlock;
import com.andresblue.tristorage.block.TerminalBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.item.DimensionalAntennaItem;
import com.andresblue.tristorage.item.StorageCoreBlockItem;
import com.andresblue.tristorage.item.TerminalBlockItem;
import com.andresblue.tristorage.recipe.CoreUpgradeRecipe;
import com.andresblue.tristorage.recipe.CleanTerminalUpgradeRecipe;
import com.andresblue.tristorage.network.TerminalPackets;
import com.andresblue.tristorage.screen.CoreScreenHandler;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.screen.LinkerScreenHandler;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.storage.StorageTier;
import com.andresblue.tristorage.storage.RemoteChunkLease;
import com.andresblue.tristorage.storage.CoreStorageData;
import com.andresblue.tristorage.storage.RemoteAccessManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class TriStorageMod implements ModInitializer {
    public static final String MOD_ID = "tristorage";

    public static final ComponentType<CoreStorageData> CORE_STORAGE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            id("core_storage"),
            ComponentType.<CoreStorageData>builder()
                    .codec(CoreStorageData.CODEC)
                    .packetCodec(PacketCodecs.registryCodec(CoreStorageData.CODEC))
                    .cache()
                    .build()
    );

    public static final StorageCoreBlock IRON_CORE = new StorageCoreBlock(StorageTier.IRON,
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.5f)
                    .registryKey(blockKey("iron_storage_core")));
    public static final StorageCoreBlock DIAMOND_CORE = new StorageCoreBlock(StorageTier.DIAMOND,
            AbstractBlock.Settings.copy(Blocks.DIAMOND_BLOCK).strength(4.5f)
                    .registryKey(blockKey("diamond_storage_core")));
    public static final StorageCoreBlock BLAZE_CORE = new StorageCoreBlock(StorageTier.BLAZE,
            AbstractBlock.Settings.copy(Blocks.OBSIDIAN).strength(12.0f, 50.0f)
                    .luminance(state -> 4).registryKey(blockKey("blaze_storage_core")));
    public static final StorageCoreBlock COSMIC_CORE = new StorageCoreBlock(StorageTier.COSMIC,
            AbstractBlock.Settings.copy(Blocks.OBSIDIAN).strength(18.0f, 1200.0f)
                    .luminance(state -> 7).registryKey(blockKey("cosmic_storage_core")));
    public static final TerminalBlock TERMINAL = new TerminalBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.5f)
                    .luminance(state -> 5).registryKey(blockKey("storage_terminal")));
    public static final LinkerBlock LINKER = new LinkerBlock(
            AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).strength(3.0f)
                    .luminance(state -> 3).registryKey(blockKey("storage_linker")));
    public static final CraftingTerminalBlock CRAFTING_TERMINAL = new CraftingTerminalBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.5f)
                    .luminance(state -> 7).registryKey(blockKey("crafting_terminal")));
    public static final RemoteTabletItem REMOTE_TABLET = new RemoteTabletItem(
            new Item.Settings().maxCount(1).registryKey(itemKey("remote_tablet")));
    public static final Item DIMENSIONAL_ANTENNA = new DimensionalAntennaItem(
            new Item.Settings().maxCount(1).registryKey(itemKey("dimensional_antenna")));
    public static final SimpleParticleType CONVERGING_PORTAL_PARTICLE = FabricParticleTypes.simple();
    public static final SimpleParticleType CONVERGING_END_PARTICLE = FabricParticleTypes.simple();

    public static BlockEntityType<StorageCoreBlockEntity> STORAGE_CORE_BLOCK_ENTITY;
    public static BlockEntityType<LinkerBlockEntity> LINKER_BLOCK_ENTITY;
    public static ScreenHandlerType<CoreScreenHandler> CORE_SCREEN_HANDLER;
    public static ScreenHandlerType<TerminalScreenHandler> TERMINAL_SCREEN_HANDLER;
    public static ScreenHandlerType<LinkerScreenHandler> LINKER_SCREEN_HANDLER;
    public static ScreenHandlerType<CraftingTerminalScreenHandler> CRAFTING_TERMINAL_SCREEN_HANDLER;
    public static ItemGroup TRISTORAGE_ITEM_GROUP;
    public static RecipeSerializer<CoreUpgradeRecipe> CORE_UPGRADE_RECIPE_SERIALIZER;
    public static RecipeSerializer<CleanTerminalUpgradeRecipe> CLEAN_TERMINAL_UPGRADE_RECIPE_SERIALIZER;

    @Override
    public void onInitialize() {
        RemoteAccessManager.initialize();
        TerminalPackets.initialize();
        CORE_UPGRADE_RECIPE_SERIALIZER = Registry.register(
                Registries.RECIPE_SERIALIZER,
                id("core_upgrade"),
                new CoreUpgradeRecipe.Serializer()
        );
        CLEAN_TERMINAL_UPGRADE_RECIPE_SERIALIZER = Registry.register(
                Registries.RECIPE_SERIALIZER,
                id("clean_terminal_upgrade"),
                new CleanTerminalUpgradeRecipe.Serializer()
        );
        registerCoreBlock("iron_storage_core", IRON_CORE);
        registerCoreBlock("diamond_storage_core", DIAMOND_CORE);
        registerCoreBlock("blaze_storage_core", BLAZE_CORE);
        registerCoreBlock("cosmic_storage_core", COSMIC_CORE);
        registerBlock("storage_terminal", TERMINAL,
                new TerminalBlockItem(TERMINAL,
                        new Item.Settings().registryKey(itemKey("storage_terminal"))));
        registerBlock("storage_linker", LINKER);
        registerBlock("crafting_terminal", CRAFTING_TERMINAL);
        Registry.register(Registries.ITEM, id("remote_tablet"), REMOTE_TABLET);
        Registry.register(Registries.ITEM, id("dimensional_antenna"), DIMENSIONAL_ANTENNA);
        Registry.register(Registries.PARTICLE_TYPE, id("converging_portal"), CONVERGING_PORTAL_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, id("converging_end"), CONVERGING_END_PARTICLE);
        Registry.register(Registries.TICKET_TYPE, id("remote_access"), RemoteChunkLease.TICKET_TYPE);

        STORAGE_CORE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                id("storage_core"),
                FabricBlockEntityTypeBuilder.create(
                        StorageCoreBlockEntity::new,
                        IRON_CORE, DIAMOND_CORE, BLAZE_CORE, COSMIC_CORE
                ).build()
        );
        LINKER_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                id("storage_linker"),
                FabricBlockEntityTypeBuilder.create(LinkerBlockEntity::new, LINKER).build()
        );
        CORE_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                id("storage_core"),
                new ScreenHandlerType<>(CoreScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );
        TERMINAL_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                id("storage_terminal"),
                new ScreenHandlerType<>(TerminalScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );
        LINKER_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                id("storage_linker"),
                new ScreenHandlerType<>(LinkerScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );
        CRAFTING_TERMINAL_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                id("crafting_terminal"),
                new ScreenHandlerType<>(CraftingTerminalScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );

        TRISTORAGE_ITEM_GROUP = Registry.register(
                Registries.ITEM_GROUP,
                id("tristorage"),
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup.tristorage"))
                        .icon(() -> new ItemStack(TERMINAL))
                        .entries((context, entries) -> {
                            entries.add(IRON_CORE);
                            entries.add(DIAMOND_CORE);
                            entries.add(BLAZE_CORE);
                            entries.add(COSMIC_CORE);
                            entries.add(TERMINAL);
                            entries.add(LINKER);
                            entries.add(CRAFTING_TERMINAL);
                            entries.add(REMOTE_TABLET);
                            entries.add(DIMENSIONAL_ANTENNA);
                        })
                        .build()
        );
    }

    private static void registerBlock(String path, Block block) {
        registerBlock(path, block,
                new BlockItem(block, new Item.Settings().registryKey(itemKey(path))));
    }

    private static void registerCoreBlock(String path, Block block) {
        registerBlock(path, block,
                new StorageCoreBlockItem(block,
                        new Item.Settings().registryKey(itemKey(path))));
    }

    private static void registerBlock(String path, Block block, Item item) {
        Registry.register(Registries.BLOCK, id(path), block);
        Registry.register(Registries.ITEM, id(path), item);
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    private static RegistryKey<Block> blockKey(String path) {
        return RegistryKey.of(RegistryKeys.BLOCK, id(path));
    }

    private static RegistryKey<Item> itemKey(String path) {
        return RegistryKey.of(RegistryKeys.ITEM, id(path));
    }
}
