package com.andresblue.tristorage;

import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.block.CraftingTerminalBlock;
import com.andresblue.tristorage.block.StorageCoreBlock;
import com.andresblue.tristorage.block.TerminalBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.item.StorageCoreBlockItem;
import com.andresblue.tristorage.item.TerminalBlockItem;
import com.andresblue.tristorage.item.DimensionalAntennaItem;
import com.andresblue.tristorage.recipe.PortableCoreUpgradeRecipe;
import com.andresblue.tristorage.recipe.CleanTerminalUpgradeRecipe;
import com.andresblue.tristorage.recipe.WirelessCraftingTerminalUpgradeRecipe;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.screen.CoreScreenHandler;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.screen.LinkerScreenHandler;
import com.andresblue.tristorage.storage.StorageTier;
import com.andresblue.tristorage.storage.StorageTickCoordinator;
import com.andresblue.tristorage.storage.StorageRepositories;
import com.andresblue.tristorage.storage.StorageDiagnostics;
import com.andresblue.tristorage.storage.RemoteAccessManager;
import com.andresblue.tristorage.storage.RemoteTerminalMode;
import com.andresblue.tristorage.network.TerminalPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

public final class TriStorageMod implements ModInitializer {
    public static final String MOD_ID = "tristorage";

    public static final StorageCoreBlock IRON_CORE = new StorageCoreBlock(StorageTier.IRON,
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.5f));
    public static final StorageCoreBlock DIAMOND_CORE = new StorageCoreBlock(StorageTier.DIAMOND,
            AbstractBlock.Settings.copy(Blocks.DIAMOND_BLOCK).strength(4.5f));
    public static final StorageCoreBlock BLAZE_CORE = new StorageCoreBlock(StorageTier.BLAZE,
            AbstractBlock.Settings.copy(Blocks.OBSIDIAN).strength(12.0f, 50.0f).luminance(state -> 4));
    public static final StorageCoreBlock COSMIC_CORE = new StorageCoreBlock(StorageTier.COSMIC,
            AbstractBlock.Settings.copy(Blocks.OBSIDIAN).strength(18.0f, 1200.0f).luminance(state -> 7));
    public static final TerminalBlock TERMINAL = new TerminalBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.5f).luminance(state -> 5));
    public static final CraftingTerminalBlock CRAFTING_TERMINAL = new CraftingTerminalBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.5f).luminance(state -> 5));
    public static final LinkerBlock LINKER = new LinkerBlock(
            AbstractBlock.Settings.copy(Blocks.COPPER_BLOCK).strength(3.0f).luminance(state -> 3));
    public static final RemoteTabletItem REMOTE_TABLET = new RemoteTabletItem(
            new Item.Settings().maxCount(1), RemoteTerminalMode.STORAGE);
    public static final RemoteTabletItem WIRELESS_CRAFTING_TERMINAL = new RemoteTabletItem(
            new Item.Settings().maxCount(1), RemoteTerminalMode.CRAFTING);
    public static final Item DIMENSIONAL_ANTENNA =
            new DimensionalAntennaItem(new Item.Settings().maxCount(16));

    public static BlockEntityType<StorageCoreBlockEntity> STORAGE_CORE_BLOCK_ENTITY;
    public static BlockEntityType<LinkerBlockEntity> LINKER_BLOCK_ENTITY;
    public static ScreenHandlerType<CoreScreenHandler> CORE_SCREEN_HANDLER;
    public static ScreenHandlerType<TerminalScreenHandler> TERMINAL_SCREEN_HANDLER;
    public static ScreenHandlerType<CraftingTerminalScreenHandler> CRAFTING_TERMINAL_SCREEN_HANDLER;
    public static ScreenHandlerType<LinkerScreenHandler> LINKER_SCREEN_HANDLER;
    public static ItemGroup TRISTORAGE_ITEM_GROUP;
    public static RecipeSerializer<PortableCoreUpgradeRecipe> CORE_UPGRADE_RECIPE_SERIALIZER;
    public static RecipeSerializer<CleanTerminalUpgradeRecipe> CRAFTING_TERMINAL_RECIPE_SERIALIZER;
    public static RecipeSerializer<WirelessCraftingTerminalUpgradeRecipe>
            WIRELESS_CRAFTING_TERMINAL_RECIPE_SERIALIZER;
    public static DefaultParticleType CONVERGING_PORTAL_PARTICLE;
    public static DefaultParticleType CONVERGING_END_PARTICLE;

    @Override
    public void onInitialize() {
        StorageTickCoordinator.initialize();
        StorageRepositories.initialize();
        StorageDiagnostics.initialize();
        RemoteAccessManager.initialize();
        TerminalPackets.initializeServer();
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(blockEntity instanceof StorageCoreBlockEntity core)) {
                return true;
            }
            boolean prepared = core.preparePortable();
            if (!prepared && !world.isClient) {
                player.sendMessage(Text.translatable(core.isRecoveryRequired()
                        ? "message.tristorage.core_recovery_required"
                        : "message.tristorage.core_not_ready"), true);
            }
            return prepared;
        });
        PlayerBlockBreakEvents.CANCELED.register((world, player, pos, state, blockEntity) -> {
            if (blockEntity instanceof StorageCoreBlockEntity core) {
                core.cancelPortablePreparation();
            }
        });
        CONVERGING_PORTAL_PARTICLE = Registry.register(
                Registries.PARTICLE_TYPE,
                id("converging_portal"),
                FabricParticleTypes.simple());
        CONVERGING_END_PARTICLE = Registry.register(
                Registries.PARTICLE_TYPE,
                id("converging_end"),
                FabricParticleTypes.simple());
        registerBlock("iron_storage_core", IRON_CORE);
        registerBlock("diamond_storage_core", DIAMOND_CORE);
        registerBlock("blaze_storage_core", BLAZE_CORE);
        registerBlock("cosmic_storage_core", COSMIC_CORE);
        registerBlock("storage_terminal", TERMINAL);
        registerBlock("crafting_terminal", CRAFTING_TERMINAL);
        registerBlock("storage_linker", LINKER);
        Registry.register(Registries.ITEM, id("remote_tablet"), REMOTE_TABLET);
        Registry.register(Registries.ITEM, id("wireless_crafting_terminal"),
                WIRELESS_CRAFTING_TERMINAL);
        Registry.register(Registries.ITEM, id("dimensional_antenna"), DIMENSIONAL_ANTENNA);

        CORE_UPGRADE_RECIPE_SERIALIZER = Registry.register(
                Registries.RECIPE_SERIALIZER,
                id("core_upgrade"),
                new PortableCoreUpgradeRecipe.Serializer()
        );
        CRAFTING_TERMINAL_RECIPE_SERIALIZER = Registry.register(
                Registries.RECIPE_SERIALIZER,
                id("clean_terminal_upgrade"),
                new CleanTerminalUpgradeRecipe.Serializer()
        );
        WIRELESS_CRAFTING_TERMINAL_RECIPE_SERIALIZER = Registry.register(
                Registries.RECIPE_SERIALIZER,
                id("wireless_crafting_terminal_upgrade"),
                new WirelessCraftingTerminalUpgradeRecipe.Serializer()
        );

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
        CRAFTING_TERMINAL_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                id("crafting_terminal"),
                new ScreenHandlerType<>(CraftingTerminalScreenHandler::new,
                        FeatureFlags.VANILLA_FEATURES)
        );
        LINKER_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                id("storage_linker"),
                new ScreenHandlerType<>(LinkerScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
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
                            entries.add(CRAFTING_TERMINAL);
                            entries.add(LINKER);
                            entries.add(DIMENSIONAL_ANTENNA);
                            entries.add(REMOTE_TABLET);
                            entries.add(WIRELESS_CRAFTING_TERMINAL);
                        })
                        .build()
        );
    }

    private static void registerBlock(String path, Block block) {
        Registry.register(Registries.BLOCK, id(path), block);
        Item item = block instanceof StorageCoreBlock
                ? new StorageCoreBlockItem(block, new Item.Settings())
                : block == TERMINAL
                ? new TerminalBlockItem(block, new Item.Settings())
                : new BlockItem(block, new Item.Settings());
        Registry.register(Registries.ITEM, id(path), item);
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
