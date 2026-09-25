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
import com.andresblue.tristorage.platform.Platform;
import com.andresblue.tristorage.platform.Registrar;
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
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loader-independent content and initialization. The loader entrypoints
 * install the platform hooks, call each {@code register*} method when their
 * loader accepts that registry, and then call {@link #initialize()}.
 */
public final class TriStorageMod {
    public static final String MOD_ID = "tristorage";

    // Cores hold whole storages, so every tier resists explosions like obsidian.
    private static final float CORE_BLAST_RESISTANCE = 1200.0f;

    public static StorageCoreBlock IRON_CORE;
    public static StorageCoreBlock DIAMOND_CORE;
    public static StorageCoreBlock BLAZE_CORE;
    public static StorageCoreBlock COSMIC_CORE;
    public static TerminalBlock TERMINAL;
    public static CraftingTerminalBlock CRAFTING_TERMINAL;
    public static LinkerBlock LINKER;
    public static RemoteTabletItem REMOTE_TABLET;
    public static RemoteTabletItem WIRELESS_CRAFTING_TERMINAL;
    public static Item DIMENSIONAL_ANTENNA;

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

    private TriStorageMod() {
    }

    /** Starts the storage runtime, diagnostics, remote access and packets. */
    public static void initialize() {
        StorageTickCoordinator.initialize();
        StorageRepositories.initialize();
        StorageDiagnostics.initialize();
        RemoteAccessManager.initialize();
        TerminalPackets.initializeServer();
    }

    public static void registerParticles(Registrar<ParticleType<?>> registrar) {
        CONVERGING_PORTAL_PARTICLE = registrar.register("converging_portal", simpleParticle());
        CONVERGING_END_PARTICLE = registrar.register("converging_end", simpleParticle());
    }

    public static void registerBlocks(Registrar<Block> registrar) {
        IRON_CORE = registrar.register("iron_storage_core", new StorageCoreBlock(StorageTier.IRON,
                BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                        .strength(3.5f, CORE_BLAST_RESISTANCE)));
        DIAMOND_CORE = registrar.register("diamond_storage_core", new StorageCoreBlock(
                StorageTier.DIAMOND, BlockBehaviour.Properties.copy(Blocks.DIAMOND_BLOCK)
                        .strength(4.5f, CORE_BLAST_RESISTANCE)));
        BLAZE_CORE = registrar.register("blaze_storage_core", new StorageCoreBlock(StorageTier.BLAZE,
                BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
                        .strength(12.0f, CORE_BLAST_RESISTANCE).lightLevel(state -> 4)));
        COSMIC_CORE = registrar.register("cosmic_storage_core", new StorageCoreBlock(
                StorageTier.COSMIC, BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
                        .strength(18.0f, CORE_BLAST_RESISTANCE).lightLevel(state -> 7)));
        TERMINAL = registrar.register("storage_terminal", new TerminalBlock(
                BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(3.5f)
                        .lightLevel(state -> 5)));
        CRAFTING_TERMINAL = registrar.register("crafting_terminal", new CraftingTerminalBlock(
                BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(3.5f)
                        .lightLevel(state -> 5)));
        LINKER = registrar.register("storage_linker", new LinkerBlock(
                BlockBehaviour.Properties.copy(Blocks.COPPER_BLOCK).strength(3.0f)
                        .lightLevel(state -> 3)));
    }

    public static void registerItems(Registrar<Item> registrar) {
        registerBlockItem(registrar, "iron_storage_core", IRON_CORE);
        registerBlockItem(registrar, "diamond_storage_core", DIAMOND_CORE);
        registerBlockItem(registrar, "blaze_storage_core", BLAZE_CORE);
        registerBlockItem(registrar, "cosmic_storage_core", COSMIC_CORE);
        registerBlockItem(registrar, "storage_terminal", TERMINAL);
        registerBlockItem(registrar, "crafting_terminal", CRAFTING_TERMINAL);
        registerBlockItem(registrar, "storage_linker", LINKER);
        REMOTE_TABLET = registrar.register("remote_tablet", new RemoteTabletItem(
                new Item.Properties().stacksTo(1), RemoteTerminalMode.STORAGE));
        WIRELESS_CRAFTING_TERMINAL = registrar.register("wireless_crafting_terminal",
                new RemoteTabletItem(new Item.Properties().stacksTo(1),
                        RemoteTerminalMode.CRAFTING));
        DIMENSIONAL_ANTENNA = registrar.register("dimensional_antenna",
                new DimensionalAntennaItem(new Item.Properties().stacksTo(16)));
    }

    public static void registerRecipeSerializers(Registrar<RecipeSerializer<?>> registrar) {
        CORE_UPGRADE_RECIPE_SERIALIZER = registrar.register("core_upgrade",
                new PortableCoreUpgradeRecipe.Serializer());
        CRAFTING_TERMINAL_RECIPE_SERIALIZER = registrar.register("clean_terminal_upgrade",
                new CleanTerminalUpgradeRecipe.Serializer());
        WIRELESS_CRAFTING_TERMINAL_RECIPE_SERIALIZER = registrar.register(
                "wireless_crafting_terminal_upgrade",
                new WirelessCraftingTerminalUpgradeRecipe.Serializer());
    }

    public static void registerBlockEntities(Registrar<BlockEntityType<?>> registrar) {
        STORAGE_CORE_BLOCK_ENTITY = registrar.register("storage_core",
                BlockEntityType.Builder.of(StorageCoreBlockEntity::new,
                        IRON_CORE, DIAMOND_CORE, BLAZE_CORE, COSMIC_CORE).build(null));
        LINKER_BLOCK_ENTITY = registrar.register("storage_linker",
                BlockEntityType.Builder.of(LinkerBlockEntity::new, LINKER).build(null));
    }

    public static void registerMenus(Registrar<MenuType<?>> registrar) {
        CORE_SCREEN_HANDLER = registrar.register("storage_core",
                new MenuType<>(CoreScreenHandler::new, FeatureFlags.VANILLA_SET));
        TERMINAL_SCREEN_HANDLER = registrar.register("storage_terminal",
                new MenuType<>(TerminalScreenHandler::new, FeatureFlags.VANILLA_SET));
        CRAFTING_TERMINAL_SCREEN_HANDLER = registrar.register("crafting_terminal",
                new MenuType<>(CraftingTerminalScreenHandler::new, FeatureFlags.VANILLA_SET));
        LINKER_SCREEN_HANDLER = registrar.register("storage_linker",
                new MenuType<>(LinkerScreenHandler::new, FeatureFlags.VANILLA_SET));
    }

    public static void registerCreativeTabs(Registrar<CreativeModeTab> registrar) {
        TRISTORAGE_ITEM_GROUP = registrar.register("tristorage", Platform.get().creativeTabBuilder()
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
                .build());
    }

    /**
     * Runs before a player breaks a block. Returns false to refuse the break.
     * A Core only leaves when it can become a portable item that keeps its
     * storage.
     */
    public static boolean beforeBlockBreak(Level level, Player player, BlockState state,
                                           BlockEntity blockEntity) {
        if (!(blockEntity instanceof StorageCoreBlockEntity core)) {
            return true;
        }
        if (!StorageCoreBlock.canBreakSafely(player, state)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.tristorage.core_needs_tool"), true);
            }
            return false;
        }
        boolean prepared = core.preparePortable();
        if (!prepared && !level.isClientSide) {
            player.displayClientMessage(Component.translatable(core.isRecoveryRequired()
                    ? "message.tristorage.core_recovery_required"
                    : "message.tristorage.core_not_ready"), true);
        }
        return prepared;
    }

    /** Reverts {@link #beforeBlockBreak} when another listener cancels the break. */
    public static void afterBlockBreakCanceled(BlockEntity blockEntity) {
        if (blockEntity instanceof StorageCoreBlockEntity core) {
            core.cancelPortablePreparation();
        }
    }

    private static void registerBlockItem(Registrar<Item> registrar, String path, Block block) {
        Item item = block instanceof StorageCoreBlock
                ? new StorageCoreBlockItem(block, new Item.Properties())
                : block == TERMINAL
                ? new TerminalBlockItem(block, new Item.Properties())
                : new BlockItem(block, new Item.Properties());
        registrar.register(path, item);
    }

    private static SimpleParticleType simpleParticle() {
        // The constructor is protected; an empty subclass is how loaders create one.
        return new SimpleParticleType(false) {
        };
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
