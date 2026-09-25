package com.andresblue.tristorage.version;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

/**
 * Item stacks that TriStorage saves outside vanilla's own item slots. Vanilla
 * cannot see them when it upgrades a world, so they are written together
 * with the data version of the game that saved them and passed through the
 * same data fixers when a newer Minecraft reads them.
 */
public final class SavedStacks {
    public static final String DATA_VERSION_KEY = "DataVersion";
    /** Minecraft 1.20.1: TriStorage builds before 2.0 recorded no version. */
    private static final int UNRECORDED_VERSION = 3465;

    private SavedStacks() {
    }

    public static int currentVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    /**
     * Reads a stack saved by the given data version; zero means the writer
     * recorded none. Those stacks are taken to be from 1.20.1 when they still
     * use the pre-1.20.5 layout, and from this version otherwise.
     */
    public static ItemStack parse(CompoundTag stack, int writtenBy,
                                  HolderLookup.Provider registries) {
        return Stacks.parse(upgrade(stack, writtenBy), registries);
    }

    static CompoundTag upgrade(CompoundTag stack, int writtenBy) {
        int current = currentVersion();
        int version = writtenBy > 0 ? writtenBy : unrecordedVersion(stack, current);
        if (version >= current || stack.isEmpty()) {
            return stack;
        }
        Tag upgraded = DataFixers.getDataFixer().update(References.ITEM_STACK,
                new Dynamic<Tag>(NbtOps.INSTANCE, stack), version, current).getValue();
        return upgraded instanceof CompoundTag tag ? tag : stack;
    }

    private static int unrecordedVersion(CompoundTag stack, int current) {
        boolean legacyLayout = stack.contains("Count", Tag.TAG_BYTE) && !stack.contains("count");
        return legacyLayout ? Math.min(UNRECORDED_VERSION, current) : current;
    }
}
