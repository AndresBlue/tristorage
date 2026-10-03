package com.andresblue.tristorage.version;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Reads NBT the same way on every version. Since Minecraft 1.21.5 the typed
 * getters return Optional and {@code contains} takes no tag type; these keep
 * the older behavior: 0, "", false or an empty tag for a missing or mistyped
 * value.
 */
public final class Nbt {
    private Nbt() {
    }

    public static int getInt(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getIntOr(key, 0);
        *///?} else {
        return tag.getInt(key);
        //?}
    }

    public static long getLong(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getLongOr(key, 0L);
        *///?} else {
        return tag.getLong(key);
        //?}
    }

    public static String getString(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getStringOr(key, "");
        *///?} else {
        return tag.getString(key);
        //?}
    }

    /** The text of a string tag. */
    public static String string(Tag tag) {
        //? if >=1.21.5 {
        /*return tag.asString().orElse("");
        *///?} else {
        return tag.getAsString();
        //?}
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getBooleanOr(key, false);
        *///?} else {
        return tag.getBoolean(key);
        //?}
    }

    public static CompoundTag getCompound(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.getCompoundOrEmpty(key);
        *///?} else {
        return tag.getCompound(key);
        //?}
    }

    /** A list of compounds; empty when missing or holding another type. */
    public static ListTag getCompoundList(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*ListTag list = tag.getListOrEmpty(key);
        return list.isEmpty() || list.get(0) instanceof CompoundTag ? list : new ListTag();
        *///?} else {
        return tag.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        //?}
    }

    public static CompoundTag getCompound(ListTag list, int index) {
        //? if >=1.21.5 {
        /*return list.getCompoundOrEmpty(index);
        *///?} else {
        return list.getCompound(index);
        //?}
    }

    public static Set<String> keys(CompoundTag tag) {
        //? if >=1.21.5 {
        /*return tag.keySet();
        *///?} else {
        return tag.getAllKeys();
        //?}
    }

    public static boolean hasNumber(CompoundTag tag, String key) {
        return tag.get(key) instanceof NumericTag;
    }

    public static boolean hasInt(CompoundTag tag, String key) {
        return tag.get(key) instanceof IntTag;
    }

    public static boolean hasLong(CompoundTag tag, String key) {
        return tag.get(key) instanceof LongTag;
    }

    public static boolean hasString(CompoundTag tag, String key) {
        return tag.get(key) instanceof StringTag;
    }

    public static boolean hasList(CompoundTag tag, String key) {
        return tag.get(key) instanceof ListTag;
    }

    public static boolean hasCompound(CompoundTag tag, String key) {
        return tag.get(key) instanceof CompoundTag;
    }
}
