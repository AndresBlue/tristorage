package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.TriStorageMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import com.andresblue.tristorage.version.Stacks;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the small, explicit part of a core block entity that is allowed to
 * travel inside an item. Coordinates, block-entity ids and unrelated data are
 * deliberately excluded when an upgrade copies a core.
 */
public final class PortableCoreData {
    public static final String CHESTS_KEY = "InstalledChests";
    public static final String ENTRIES_KEY = "Entries";
    public static final String STORAGE_ID_KEY = "TriStorageId";
    public static final String OWNERSHIP_TOKEN_KEY = "OwnershipToken";
    public static final String FORMAT_VERSION_KEY = "TriStorageFormat";
    /** Data version of the game that saved the stacks in {@link #ENTRIES_KEY}. */
    public static final String DATA_VERSION_KEY = "TriStorageDataVersion";
    public static final String TYPES_SUMMARY_KEY = "StoredTypesSummary";
    public static final String ITEMS_SUMMARY_KEY = "TotalItemsSummary";
    private static final String NEOFORGE_PORT_PAYLOAD = "TriStorageCore";

    private PortableCoreData() {
    }

    public static CompoundTag copyFrom(ItemStack stack) {
        return sanitizedCopy(carried(stack));
    }

    /**
     * The storage an item carries: its block entity data, or where the NeoForge
     * 1.21.1 port (TriStorage 1.x) kept it, in custom data directly or under
     * {@code TriStorageCore}.
     */
    @Nullable
    private static CompoundTag carried(ItemStack stack) {
        CompoundTag data = Stacks.blockEntityData(stack);
        if (data != null) {
            return data.contains(NEOFORGE_PORT_PAYLOAD, Tag.TAG_COMPOUND)
                    ? data.getCompound(NEOFORGE_PORT_PAYLOAD) : data;
        }
        CompoundTag custom = Stacks.customData(stack);
        if (custom.contains(NEOFORGE_PORT_PAYLOAD, Tag.TAG_COMPOUND)) {
            return custom.getCompound(NEOFORGE_PORT_PAYLOAD);
        }
        return custom.contains(CHESTS_KEY, Tag.TAG_ANY_NUMERIC)
                || custom.contains(ENTRIES_KEY, Tag.TAG_LIST) ? custom : null;
    }

    /**
     * Moves storage kept in a NeoForge port layout into the block entity data
     * a Core is placed from. Other custom data stays where it is.
     */
    public static void adoptPortLayout(ItemStack stack) {
        CompoundTag blockEntityData = Stacks.blockEntityData(stack);
        if (blockEntityData != null && !blockEntityData.contains(NEOFORGE_PORT_PAYLOAD)) {
            return;
        }
        CompoundTag carried = carried(stack);
        if (carried == null) {
            return;
        }
        CompoundTag custom = Stacks.customData(stack);
        custom.remove(NEOFORGE_PORT_PAYLOAD);
        custom.remove(CHESTS_KEY);
        custom.remove(ENTRIES_KEY);
        Stacks.setCustomData(stack, custom);
        applyTo(stack, carried);
    }

    public static CompoundTag sanitizedCopy(CompoundTag blockEntityData) {
        if (blockEntityData == null) {
            return null;
        }
        CompoundTag portable = new CompoundTag();
        if (blockEntityData.contains(CHESTS_KEY, Tag.TAG_ANY_NUMERIC)) {
            portable.putInt(CHESTS_KEY, Math.max(0, blockEntityData.getInt(CHESTS_KEY)));
        }
        if (blockEntityData.contains(ENTRIES_KEY, Tag.TAG_LIST)) {
            portable.put(ENTRIES_KEY, blockEntityData.getList(
                    ENTRIES_KEY, Tag.TAG_COMPOUND).copy());
        }
        if (blockEntityData.contains(STORAGE_ID_KEY, Tag.TAG_STRING)) {
            portable.putString(STORAGE_ID_KEY, blockEntityData.getString(STORAGE_ID_KEY));
        }
        if (blockEntityData.contains(OWNERSHIP_TOKEN_KEY, Tag.TAG_STRING)) {
            portable.putString(OWNERSHIP_TOKEN_KEY,
                    blockEntityData.getString(OWNERSHIP_TOKEN_KEY));
        }
        portable.putInt(FORMAT_VERSION_KEY,
                Math.max(1, blockEntityData.getInt(FORMAT_VERSION_KEY)));
        if (blockEntityData.contains(DATA_VERSION_KEY, Tag.TAG_INT)) {
            portable.putInt(DATA_VERSION_KEY, blockEntityData.getInt(DATA_VERSION_KEY));
        }
        if (blockEntityData.contains(TYPES_SUMMARY_KEY, Tag.TAG_ANY_NUMERIC)) {
            portable.putInt(TYPES_SUMMARY_KEY,
                    Math.max(0, blockEntityData.getInt(TYPES_SUMMARY_KEY)));
        }
        if (blockEntityData.contains(ITEMS_SUMMARY_KEY, Tag.TAG_ANY_NUMERIC)) {
            portable.putLong(ITEMS_SUMMARY_KEY,
                    Math.max(0, blockEntityData.getLong(ITEMS_SUMMARY_KEY)));
        }
        return portable.isEmpty() ? null : portable;
    }

    public static void applyTo(ItemStack stack, CompoundTag portable) {
        CompoundTag sanitized = sanitizedCopy(portable);
        if (sanitized != null && !sanitized.isEmpty()) {
            Stacks.setBlockEntityData(
                    stack, TriStorageMod.STORAGE_CORE_BLOCK_ENTITY, sanitized);
        }
    }

    public static int installedChests(ItemStack stack) {
        CompoundTag data = carried(stack);
        return data == null ? 0 : Math.max(0, data.getInt(CHESTS_KEY));
    }

    public static int storedTypes(ItemStack stack) {
        CompoundTag data = carried(stack);
        if (data == null) {
            return 0;
        }
        return data.contains(TYPES_SUMMARY_KEY, Tag.TAG_ANY_NUMERIC)
                ? Math.max(0, data.getInt(TYPES_SUMMARY_KEY))
                : data.getList(ENTRIES_KEY, Tag.TAG_COMPOUND).size();
    }

    public static long totalItems(ItemStack stack) {
        CompoundTag data = carried(stack);
        if (data == null) {
            return 0;
        }
        if (data.contains(ITEMS_SUMMARY_KEY, Tag.TAG_ANY_NUMERIC)) {
            return Math.max(0, data.getLong(ITEMS_SUMMARY_KEY));
        }
        long total = 0;
        var entries = data.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < entries.size(); index++) {
            long count = Math.max(0, entries.getCompound(index).getLong("Count"));
            total = Long.MAX_VALUE - total < count ? Long.MAX_VALUE : total + count;
        }
        return total;
    }
}
