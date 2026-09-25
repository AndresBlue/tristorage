package com.andresblue.tristorage.version;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
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
    /**
     * TriStorage builds before 2.0 recorded no version. Those that saved the
     * pre-1.20.5 stack layout are taken to be 1.20.1; the only ones that saved
     * components, the Fabric and NeoForge ports, were for 1.21.1.
     */
    private static final int UNRECORDED_LEGACY_LAYOUT = 3465;
    private static final int UNRECORDED_COMPONENT_LAYOUT = 3955;

    private SavedStacks() {
    }

    public static int currentVersion() {
        return SharedConstants.WORLD_VERSION;
    }

    /** Reads a stack saved by the given data version; zero means the writer recorded none. */
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
        boolean legacyLayout = stack.get("Count") instanceof ByteTag && !stack.contains("count");
        return Math.min(legacyLayout ? UNRECORDED_LEGACY_LAYOUT : UNRECORDED_COMPONENT_LAYOUT,
                current);
    }
}
