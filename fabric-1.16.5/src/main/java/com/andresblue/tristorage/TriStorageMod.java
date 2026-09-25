package com.andresblue.tristorage;

import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.block.StorageCoreBlock;
import com.andresblue.tristorage.block.TerminalBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.screen.CoreScreenHandler;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.StorageTier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

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
    public static final LinkerBlock LINKER = new LinkerBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.0f).luminance(state -> 3));
    public static final RemoteTabletItem REMOTE_TABLET = new RemoteTabletItem(new Item.Settings().maxCount(1));

    public static BlockEntityType<StorageCoreBlockEntity> STORAGE_CORE_BLOCK_ENTITY;
    public static ScreenHandlerType<CoreScreenHandler> CORE_SCREEN_HANDLER;
    public static ScreenHandlerType<TerminalScreenHandler> TERMINAL_SCREEN_HANDLER;
    public static ItemGroup TRISTORAGE_ITEM_GROUP;

    @Override
    public void onInitialize() {
        registerBlock("iron_storage_core", IRON_CORE);
        registerBlock("diamond_storage_core", DIAMOND_CORE);
        registerBlock("blaze_storage_core", BLAZE_CORE);
        registerBlock("cosmic_storage_core", COSMIC_CORE);
        registerBlock("storage_terminal", TERMINAL);
        registerBlock("storage_linker", LINKER);
        Registry.register(Registry.ITEM, id("remote_tablet"), REMOTE_TABLET);

        STORAGE_CORE_BLOCK_ENTITY = Registry.register(
                Registry.BLOCK_ENTITY_TYPE,
                id("storage_core"),
                new BlockEntityType<>(
                        StorageCoreBlockEntity::new,
                        ImmutableSet.of(IRON_CORE, DIAMOND_CORE, BLAZE_CORE, COSMIC_CORE),
                        null
                )
        );
        CORE_SCREEN_HANDLER = ScreenHandlerRegistry.registerSimple(
                id("storage_core"), CoreScreenHandler::new);
        TERMINAL_SCREEN_HANDLER = ScreenHandlerRegistry.registerSimple(
                id("storage_terminal"), TerminalScreenHandler::new);

        TRISTORAGE_ITEM_GROUP = FabricItemGroupBuilder.create(id("tristorage"))
                .icon(() -> new ItemStack(TERMINAL))
                .appendItems(entries -> {
                    entries.add(new ItemStack(IRON_CORE));
                    entries.add(new ItemStack(DIAMOND_CORE));
                    entries.add(new ItemStack(BLAZE_CORE));
                    entries.add(new ItemStack(COSMIC_CORE));
                    entries.add(new ItemStack(TERMINAL));
                    entries.add(new ItemStack(LINKER));
                    entries.add(new ItemStack(REMOTE_TABLET));
                })
                .build();
    }

    private static void registerBlock(String path, Block block) {
        Registry.register(Registry.BLOCK, id(path), block);
        Registry.register(Registry.ITEM, id(path), new BlockItem(block, new Item.Settings()));
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
