package com.andresblue.tristorage.version;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
//? if >=1.19.3 {
import net.minecraft.core.registries.BuiltInRegistries;
//?}

/**
 * The game's built-in registries. They were static fields of {@code Registry}
 * until Minecraft 1.19.3 and live in {@code BuiltInRegistries} since.
 */
public final class GameRegistries {
    //? if >=1.19.3 {
    public static final Registry<Item> ITEM = BuiltInRegistries.ITEM;
    public static final Registry<Block> BLOCK = BuiltInRegistries.BLOCK;
    public static final Registry<BlockEntityType<?>> BLOCK_ENTITY_TYPE =
            BuiltInRegistries.BLOCK_ENTITY_TYPE;
    public static final Registry<MenuType<?>> MENU = BuiltInRegistries.MENU;
    public static final Registry<ParticleType<?>> PARTICLE_TYPE = BuiltInRegistries.PARTICLE_TYPE;
    public static final Registry<RecipeSerializer<?>> RECIPE_SERIALIZER =
            BuiltInRegistries.RECIPE_SERIALIZER;
    //?} else {
    /*public static final Registry<Item> ITEM = Registry.ITEM;
    public static final Registry<Block> BLOCK = Registry.BLOCK;
    public static final Registry<BlockEntityType<?>> BLOCK_ENTITY_TYPE = Registry.BLOCK_ENTITY_TYPE;
    public static final Registry<MenuType<?>> MENU = Registry.MENU;
    public static final Registry<ParticleType<?>> PARTICLE_TYPE = Registry.PARTICLE_TYPE;
    public static final Registry<RecipeSerializer<?>> RECIPE_SERIALIZER = Registry.RECIPE_SERIALIZER;
    *///?}

    private GameRegistries() {
    }
}
