package com.andresblue.tristorage.version;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
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
    /** Data version of Minecraft 1.21.11, which the Fabric port for it wrote. */
    public static final int FABRIC_1_21_11_PORT = 4671;
    /** Data version of Minecraft 1.21.1, which the Fabric and NeoForge ports for it wrote. */
    public static final int PORTS_1_21_1 = 3955;

    /**
     * TriStorage before 2.0 recorded no version. These are the games its
     * builds ran on, newest first: 1.20.1, 1.18.2 and 1.16.5 saved the stack
     * layout that preceded components, and the 1.21.11 and 1.21.1 ports
     * saved components.
     */
    private static final int[] LEGACY_LAYOUT_WRITERS = {3465, 2975, 2586};
    private static final int[] COMPONENT_LAYOUT_WRITERS = {FABRIC_1_21_11_PORT, PORTS_1_21_1};
    /** Minecraft 1.21.5, where text stopped being saved as a JSON string. */
    private static final int STRUCTURED_TEXT = 4325;

    private SavedStacks() {
    }

    public static int currentVersion() {
        return SharedConstants.WORLD_VERSION;
    }

    /** Reads a stack saved by the given data version; zero means the writer recorded none. */
    public static ItemStack parse(CompoundTag stack, int writtenBy,
                                  HolderLookup.Provider registries) {
        if (writtenBy > 0) {
            return Stacks.parse(upgrade(stack, writtenBy), registries);
        }
        return parseUnrecorded(stack, registries);
    }

    /**
     * Reads a stack from a build that recorded no version. Nothing says which
     * game wrote it, so it is taken as written by the newest candidate whose
     * data fixers leave something this game accepts in full: fixing data that
     * is already newer can strip enchantments and names, and reading data that
     * is older without fixing it loses renamed items.
     */
    private static ItemStack parseUnrecorded(CompoundTag stack, HolderLookup.Provider registries) {
        boolean legacyLayout = stack.get("Count") instanceof ByteTag && !stack.contains("count");
        int[] writers = legacyLayout ? LEGACY_LAYOUT_WRITERS : COMPONENT_LAYOUT_WRITERS;
        boolean jsonText = !legacyLayout && hasJsonText(stack);
        int current = currentVersion();
        int oldest = writers[writers.length - 1];
        for (int writer : writers) {
            // A stack cannot come from a newer game, and text saved as a JSON
            // string still reads as plain text after 1.21.5: only its form tells.
            if (writer > current || jsonText && writer >= STRUCTURED_TEXT) {
                continue;
            }
            ItemStack parsed = Stacks.parseStrict(upgrade(stack, writer), registries);
            if (parsed != null) {
                return parsed;
            }
        }
        // No candidate fits; let the oldest one report what is wrong.
        return Stacks.parse(upgrade(stack, Math.min(oldest, current)), registries);
    }

    static CompoundTag upgrade(CompoundTag stack, int writtenBy) {
        int current = currentVersion();
        if (writtenBy >= current || stack.isEmpty()) {
            return stack;
        }
        Tag upgraded = DataFixers.getDataFixer().update(References.ITEM_STACK,
                new Dynamic<Tag>(NbtOps.INSTANCE, stack), writtenBy, current).getValue();
        return upgraded instanceof CompoundTag tag ? tag : stack;
    }

    /** Whether the stack's name or lore is JSON in a string, as before Minecraft 1.21.5. */
    private static boolean hasJsonText(CompoundTag stack) {
        CompoundTag components = Nbt.getCompound(stack, "components");
        if (isJsonText(components.get("minecraft:custom_name"))
                || isJsonText(components.get("minecraft:item_name"))) {
            return true;
        }
        if (components.get("minecraft:lore") instanceof ListTag lore) {
            for (Tag line : lore) {
                if (isJsonText(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isJsonText(Tag tag) {
        if (!(tag instanceof StringTag)) {
            return false;
        }
        String text = Nbt.string(tag);
        return !text.isEmpty() && "\"{[".indexOf(text.charAt(0)) >= 0;
    }
}
