package com.andresblue.tristorage.version;

import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Player accessors that moved. ServerPlayer lost {@code serverLevel()} and
 * {@code getServer()} in Minecraft 1.21.9, while the level works on every
 * version; the inventory's item list became private in 1.21.5.
 */
public final class Players {
    private Players() {
    }

    public static ServerLevel level(ServerPlayer player) {
        return (ServerLevel) level((Entity) player);
    }

    /** The level an entity is in: a field before Minecraft 1.20, an accessor since. */
    public static Level level(Entity entity) {
        //? if >=1.20 {
        return entity.level();
        //?} else {
        /*return entity.level;
        *///?}
    }

    public static MinecraftServer server(ServerPlayer player) {
        return level(player).getServer();
    }

    /** The 36 main inventory slots, hotbar included; not armor or offhand. */
    public static NonNullList<ItemStack> mainItems(Player player) {
        //? if >=1.21.5 {
        /*return player.getInventory().getNonEquipmentItems();
        *///?} else {
        return player.getInventory().items;
        //?}
    }
}
