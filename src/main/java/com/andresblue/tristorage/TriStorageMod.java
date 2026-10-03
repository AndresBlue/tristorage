package com.andresblue.tristorage;

import com.andresblue.tristorage.version.Ids;
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
import com.andresblue.tristorage.storage.RemoteChunkLease;
import com.andresblue.tristorage.network.TerminalPackets;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
//? if >=1.21.2 {
/*import net.minecraft.core.registries.Registries;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
//? if >=1.21.5 {
/*import net.minecraft.server.level.TicketType;
*///?}
import net.minecraft.world.entity.player.Player;
//? if >=1.19.3 {
import net.minecraft.world.flag.FeatureFlags;
//?}
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

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
                copyOf("iron_storage_core", Blocks.IRON_BLOCK)
                        .strength(3.5f, CORE_BLAST_RESISTANCE)));
        DIAMOND_CORE = registrar.register("diamond_storage_core", new StorageCoreBlock(
                StorageTier.DIAMOND,
                copyOf("diamond_storage_core", Blocks.DIAMOND_BLOCK)
                        .strength(4.5f, CORE_BLAST_RESISTANCE)));
        BLAZE_CORE = registrar.register("blaze_storage_core", new StorageCoreBlock(
                StorageTier.BLAZE,
                copyOf("blaze_storage_core", Blocks.OBSIDIAN)
                        .strength(12.0f, CORE_BLAST_RESISTANCE).lightLevel(state -> 4)));
        COSMIC_CORE = registrar.register("cosmic_storage_core", new StorageCoreBlock(
                StorageTier.COSMIC,
                copyOf("cosmic_storage_core", Blocks.OBSIDIAN)
                        .strength(18.0f, CORE_BLAST_RESISTANCE).lightLevel(state -> 7)));
        TERMINAL = registrar.register("storage_terminal", new TerminalBlock(
                copyOf("storage_terminal", Blocks.IRON_BLOCK).strength(3.5f)
                        .lightLevel(state -> 5)));
        CRAFTING_TERMINAL = registrar.register("crafting_terminal", new CraftingTerminalBlock(
                copyOf("crafting_terminal", Blocks.IRON_BLOCK).strength(3.5f)
                        .lightLevel(state -> 5)));
        LINKER = registrar.register("storage_linker", new LinkerBlock(
                copyOf("storage_linker", Blocks.COPPER_BLOCK).strength(3.0f)
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
                itemProperties("remote_tablet").stacksTo(1), RemoteTerminalMode.STORAGE));
        WIRELESS_CRAFTING_TERMINAL = registrar.register("wireless_crafting_terminal",
                new RemoteTabletItem(itemProperties("wireless_crafting_terminal").stacksTo(1),
                        RemoteTerminalMode.CRAFTING));
        DIMENSIONAL_ANTENNA = registrar.register("dimensional_antenna",
                new DimensionalAntennaItem(itemProperties("dimensional_antenna").stacksTo(16)));
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
                Platform.get().blockEntityType(StorageCoreBlockEntity::new,
                        IRON_CORE, DIAMOND_CORE, BLAZE_CORE, COSMIC_CORE));
        LINKER_BLOCK_ENTITY = registrar.register("storage_linker",
                Platform.get().blockEntityType(LinkerBlockEntity::new, LINKER));
    }

    //? if >=1.21.5 {
    /*// Since 1.21.5 chunk ticket types are registered.
    public static void registerTicketTypes(Registrar<TicketType> registrar) {
        registrar.register("remote_access", RemoteChunkLease.TICKET_TYPE);
    }
    *///?}

    public static void registerMenus(Registrar<MenuType<?>> registrar) {
        CORE_SCREEN_HANDLER = registrar.register("storage_core",
                menuType(CoreScreenHandler::new));
        TERMINAL_SCREEN_HANDLER = registrar.register("storage_terminal",
                menuType(TerminalScreenHandler::new));
        CRAFTING_TERMINAL_SCREEN_HANDLER = registrar.register("crafting_terminal",
                menuType(CraftingTerminalScreenHandler::new));
        LINKER_SCREEN_HANDLER = registrar.register("storage_linker",
                menuType(LinkerScreenHandler::new));
    }

    //? if >=1.20 {
    public static void registerCreativeTabs(Registrar<CreativeModeTab> registrar) {
        TRISTORAGE_ITEM_GROUP = registrar.register("tristorage", Platform.get().creativeTabBuilder()
                .title(Component.translatable("itemGroup.tristorage"))
                .icon(() -> new ItemStack(TERMINAL))
                .displayItems((context, entries) -> creativeTabItems().forEach(entries::accept))
                .build());
    }
    //?} else {
    /*// Before 1.20 a creative tab is created rather than registered, and every
    // item names the tab it sits in, so the tab has to exist before the items.
    public static void createCreativeTab() {
        TRISTORAGE_ITEM_GROUP = Platform.get().creativeTab(id("tristorage"),
                () -> new ItemStack(TERMINAL), TriStorageMod::creativeTabItems);
    }
    *///?}

    /** The tab's contents, in display order. */
    private static List<ItemLike> creativeTabItems() {
        return List.of(IRON_CORE, DIAMOND_CORE, BLAZE_CORE, COSMIC_CORE, TERMINAL,
                CRAFTING_TERMINAL, LINKER, DIMENSIONAL_ANTENNA, REMOTE_TABLET,
                WIRELESS_CRAFTING_TERMINAL);
    }

    private static <T extends AbstractContainerMenu> MenuType<T> menuType(
            MenuType.MenuSupplier<T> factory) {
        //? if >=1.19.3 {
        return new MenuType<>(factory, FeatureFlags.VANILLA_SET);
        //?} else {
        /*return new MenuType<>(factory);
        *///?}
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
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                        "message.tristorage.core_needs_tool"), true);
            }
            return false;
        }
        boolean prepared = core.preparePortable();
        if (!prepared && !level.isClientSide()) {
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
                ? new StorageCoreBlockItem(block, blockItemProperties(path))
                : block == TERMINAL
                ? new TerminalBlockItem(block, blockItemProperties(path))
                : new BlockItem(block, blockItemProperties(path));
        registrar.register(path, item);
    }

    /** The base block's properties for the block registered under {@code path}. */
    private static BlockBehaviour.Properties copyOf(String path, Block block) {
        //? if >=1.20.5 {
        /*BlockBehaviour.Properties properties = BlockBehaviour.Properties.ofFullCopy(block);
        *///?} else {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.copy(block);
        //?}
        //? if >=1.21.2 {
        /*// Since 1.21.2 a block must know its id before it is created.
        properties.setId(ResourceKey.create(Registries.BLOCK, id(path)));
        *///?}
        return properties;
    }

    private static Item.Properties itemProperties(String path) {
        Item.Properties properties = new Item.Properties();
        //? if <1.20 {
        /*properties.tab(TRISTORAGE_ITEM_GROUP);
        *///?}
        //? if >=1.21.2 {
        /*properties.setId(ResourceKey.create(Registries.ITEM, id(path)));
        *///?}
        return properties;
    }

    /** Block items keep the block's translation key, as before 1.21.2. */
    private static Item.Properties blockItemProperties(String path) {
        //? if >=1.21.2 {
        /*return itemProperties(path).useBlockDescriptionPrefix();
        *///?} else {
        return itemProperties(path);
        //?}
    }

    private static SimpleParticleType simpleParticle() {
        // The constructor is protected; an empty subclass is how loaders create one.
        return new SimpleParticleType(false) {
        };
    }

    public static ResourceLocation id(String path) {
        return Ids.of(MOD_ID, path);
    }
}
