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
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class TriStorageMod implements ModInitializer {
    public static final String MOD_ID = "tristorage";

    // Cores hold whole storages, so every tier resists explosions like obsidian.
    private static final float CORE_BLAST_RESISTANCE = 1200.0f;
    public static final StorageCoreBlock IRON_CORE = new StorageCoreBlock(StorageTier.IRON,
            BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.5f, CORE_BLAST_RESISTANCE));
    public static final StorageCoreBlock DIAMOND_CORE = new StorageCoreBlock(StorageTier.DIAMOND,
            BlockBehaviour.Properties.copy(Blocks.DIAMOND_BLOCK)
                    .strength(4.5f, CORE_BLAST_RESISTANCE));
    public static final StorageCoreBlock BLAZE_CORE = new StorageCoreBlock(StorageTier.BLAZE,
            BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
                    .strength(12.0f, CORE_BLAST_RESISTANCE).lightLevel(state -> 4));
    public static final StorageCoreBlock COSMIC_CORE = new StorageCoreBlock(StorageTier.COSMIC,
            BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
                    .strength(18.0f, CORE_BLAST_RESISTANCE).lightLevel(state -> 7));
    public static final TerminalBlock TERMINAL = new TerminalBlock(
            BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(3.5f).lightLevel(state -> 5));
    public static final CraftingTerminalBlock CRAFTING_TERMINAL = new CraftingTerminalBlock(
            BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(3.5f).lightLevel(state -> 5));
    public static final LinkerBlock LINKER = new LinkerBlock(
            BlockBehaviour.Properties.copy(Blocks.COPPER_BLOCK).strength(3.0f).lightLevel(state -> 3));
    public static final RemoteTabletItem REMOTE_TABLET = new RemoteTabletItem(
            new Item.Properties().stacksTo(1), RemoteTerminalMode.STORAGE);
    public static final RemoteTabletItem WIRELESS_CRAFTING_TERMINAL = new RemoteTabletItem(
            new Item.Properties().stacksTo(1), RemoteTerminalMode.CRAFTING);
    public static final Item DIMENSIONAL_ANTENNA =
            new DimensionalAntennaItem(new Item.Properties().stacksTo(16));

    public static BlockEntityType<StorageCoreBlockEntity> STORAGE_CORE_BLOCK_ENTITY;
    public static BlockEntityType<LinkerBlockEntity> LINKER_BLOCK_ENTITY;
    public static MenuType<CoreScreenHandler> CORE_SCREEN_HANDLER;
    public static MenuType<TerminalScreenHandler> TERMINAL_SCREEN_HANDLER;
    public static MenuType<CraftingTerminalScreenHandler> CRAFTING_TERMINAL_SCREEN_HANDLER;
    public static MenuType<LinkerScreenHandler> LINKER_SCREEN_HANDLER;
    public static CreativeModeTab TRISTORAGE_ITEM_GROUP;
    public static RecipeSerializer<PortableCoreUpgradeRecipe> CORE_UPGRADE_RECIPE_SERIALIZER;
    public static RecipeSerializer<CleanTerminalUpgradeRecipe> CRAFTING_TERMINAL_RECIPE_SERIALIZER;
    public static RecipeSerializer<WirelessCraftingTerminalUpgradeRecipe>
            WIRELESS_CRAFTING_TERMINAL_RECIPE_SERIALIZER;
    public static SimpleParticleType CONVERGING_PORTAL_PARTICLE;
    public static SimpleParticleType CONVERGING_END_PARTICLE;

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
            if (!StorageCoreBlock.canBreakSafely(player, state)) {
                if (!world.isClientSide) {
                    player.displayClientMessage(Component.translatable(
                            "message.tristorage.core_needs_tool"), true);
                }
                return false;
            }
            boolean prepared = core.preparePortable();
            if (!prepared && !world.isClientSide) {
                player.displayClientMessage(Component.translatable(core.isRecoveryRequired()
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
                BuiltInRegistries.PARTICLE_TYPE,
                id("converging_portal"),
                FabricParticleTypes.simple());
        CONVERGING_END_PARTICLE = Registry.register(
                BuiltInRegistries.PARTICLE_TYPE,
                id("converging_end"),
                FabricParticleTypes.simple());
        registerBlock("iron_storage_core", IRON_CORE);
        registerBlock("diamond_storage_core", DIAMOND_CORE);
        registerBlock("blaze_storage_core", BLAZE_CORE);
        registerBlock("cosmic_storage_core", COSMIC_CORE);
        registerBlock("storage_terminal", TERMINAL);
        registerBlock("crafting_terminal", CRAFTING_TERMINAL);
        registerBlock("storage_linker", LINKER);
        Registry.register(BuiltInRegistries.ITEM, id("remote_tablet"), REMOTE_TABLET);
        Registry.register(BuiltInRegistries.ITEM, id("wireless_crafting_terminal"),
                WIRELESS_CRAFTING_TERMINAL);
        Registry.register(BuiltInRegistries.ITEM, id("dimensional_antenna"), DIMENSIONAL_ANTENNA);

        CORE_UPGRADE_RECIPE_SERIALIZER = Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                id("core_upgrade"),
                new PortableCoreUpgradeRecipe.Serializer()
        );
        CRAFTING_TERMINAL_RECIPE_SERIALIZER = Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                id("clean_terminal_upgrade"),
                new CleanTerminalUpgradeRecipe.Serializer()
        );
        WIRELESS_CRAFTING_TERMINAL_RECIPE_SERIALIZER = Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                id("wireless_crafting_terminal_upgrade"),
                new WirelessCraftingTerminalUpgradeRecipe.Serializer()
        );

        STORAGE_CORE_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id("storage_core"),
                FabricBlockEntityTypeBuilder.create(
                        StorageCoreBlockEntity::new,
                        IRON_CORE, DIAMOND_CORE, BLAZE_CORE, COSMIC_CORE
                ).build()
        );
        LINKER_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id("storage_linker"),
                FabricBlockEntityTypeBuilder.create(LinkerBlockEntity::new, LINKER).build()
        );
        CORE_SCREEN_HANDLER = Registry.register(
                BuiltInRegistries.MENU,
                id("storage_core"),
                new MenuType<>(CoreScreenHandler::new, FeatureFlags.VANILLA_SET)
        );
        TERMINAL_SCREEN_HANDLER = Registry.register(
                BuiltInRegistries.MENU,
                id("storage_terminal"),
                new MenuType<>(TerminalScreenHandler::new, FeatureFlags.VANILLA_SET)
        );
        CRAFTING_TERMINAL_SCREEN_HANDLER = Registry.register(
                BuiltInRegistries.MENU,
                id("crafting_terminal"),
                new MenuType<>(CraftingTerminalScreenHandler::new,
                        FeatureFlags.VANILLA_SET)
        );
        LINKER_SCREEN_HANDLER = Registry.register(
                BuiltInRegistries.MENU,
                id("storage_linker"),
                new MenuType<>(LinkerScreenHandler::new, FeatureFlags.VANILLA_SET)
        );

        TRISTORAGE_ITEM_GROUP = Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                id("tristorage"),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.tristorage"))
                        .icon(() -> new ItemStack(TERMINAL))
                        .displayItems((context, entries) -> {
                            entries.accept(IRON_CORE);
                            entries.accept(DIAMOND_CORE);
                            entries.accept(BLAZE_CORE);
                            entries.accept(COSMIC_CORE);
                            entries.accept(TERMINAL);
                            entries.accept(CRAFTING_TERMINAL);
                            entries.accept(LINKER);
                            entries.accept(DIMENSIONAL_ANTENNA);
                            entries.accept(REMOTE_TABLET);
                            entries.accept(WIRELESS_CRAFTING_TERMINAL);
                        })
                        .build()
        );
    }

    private static void registerBlock(String path, Block block) {
        Registry.register(BuiltInRegistries.BLOCK, id(path), block);
        Item item = block instanceof StorageCoreBlock
                ? new StorageCoreBlockItem(block, new Item.Properties())
                : block == TERMINAL
                ? new TerminalBlockItem(block, new Item.Properties())
                : new BlockItem(block, new Item.Properties());
        Registry.register(BuiltInRegistries.ITEM, id(path), item);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
