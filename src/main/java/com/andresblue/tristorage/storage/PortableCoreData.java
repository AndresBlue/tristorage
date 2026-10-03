package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.version.Nbt;
import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.version.SavedStacks;
import com.andresblue.tristorage.version.Stacks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
//? if >=1.21.11 {
/*import net.minecraft.world.item.component.CustomData;
*///?}
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
     * The storage an item carries: its block entity data, or where a TriStorage
     * 1.x port kept it. The NeoForge 1.21.1 port used custom data, directly or
     * under {@code TriStorageCore}; the Fabric 1.21.11 port used an item
     * component of its own.
     */
    @Nullable
    private static CompoundTag carried(ItemStack stack) {
        CompoundTag data = Stacks.blockEntityData(stack);
        if (data != null) {
            return Nbt.hasCompound(data, NEOFORGE_PORT_PAYLOAD)
                    ? Nbt.getCompound(data, NEOFORGE_PORT_PAYLOAD) : data;
        }
        //? if >=1.21.11 {
        /*CustomData fabricPort = stack.get(TriStorageMod.LEGACY_CORE_STORAGE);
        if (fabricPort != null) {
            return fromFabricPortComponent(fabricPort.copyTag());
        }
        *///?}
        CompoundTag custom = Stacks.customData(stack);
        if (Nbt.hasCompound(custom, NEOFORGE_PORT_PAYLOAD)) {
            return Nbt.getCompound(custom, NEOFORGE_PORT_PAYLOAD);
        }
        return Nbt.hasNumber(custom, CHESTS_KEY)
                || Nbt.hasList(custom, ENTRIES_KEY) ? custom : null;
    }

    /**
     * Rewrites the Fabric 1.21.11 port's component, {@code installed_chests}
     * and {@code entries} of {@code stack} and {@code count}, with this
     * mod's keys. That port only ever ran on 1.21.11.
     */
    static CompoundTag fromFabricPortComponent(CompoundTag component) {
        CompoundTag carried = new CompoundTag();
        carried.putInt(CHESTS_KEY, Math.max(0, Nbt.getInt(component, "installed_chests")));
        ListTag entries = new ListTag();
        ListTag stored = Nbt.getCompoundList(component, "entries");
        for (int index = 0; index < stored.size(); index++) {
            CompoundTag source = Nbt.getCompound(stored, index);
            CompoundTag entry = new CompoundTag();
            entry.put("Stack", Nbt.getCompound(source, "stack").copy());
            entry.putLong("Count", Nbt.getLong(source, "count"));
            entries.add(entry);
        }
        carried.put(ENTRIES_KEY, entries);
        carried.putInt(DATA_VERSION_KEY, SavedStacks.FABRIC_1_21_11_PORT);
        return carried;
    }

    /**
     * Moves storage kept in a 1.x port's layout into the block entity data a
     * Core is placed from. Other custom data stays where it is.
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
        //? if >=1.21.11 {
        /*stack.remove(TriStorageMod.LEGACY_CORE_STORAGE);
        *///?}
        applyTo(stack, carried);
    }

    public static CompoundTag sanitizedCopy(CompoundTag blockEntityData) {
        if (blockEntityData == null) {
            return null;
        }
        CompoundTag portable = new CompoundTag();
        if (Nbt.hasNumber(blockEntityData, CHESTS_KEY)) {
            portable.putInt(CHESTS_KEY, Math.max(0, Nbt.getInt(blockEntityData, CHESTS_KEY)));
        }
        if (Nbt.hasList(blockEntityData, ENTRIES_KEY)) {
            portable.put(ENTRIES_KEY, Nbt.getCompoundList(blockEntityData, 
                    ENTRIES_KEY).copy());
        }
        if (Nbt.hasString(blockEntityData, STORAGE_ID_KEY)) {
            portable.putString(STORAGE_ID_KEY, Nbt.getString(blockEntityData, STORAGE_ID_KEY));
        }
        if (Nbt.hasString(blockEntityData, OWNERSHIP_TOKEN_KEY)) {
            portable.putString(OWNERSHIP_TOKEN_KEY,
                    Nbt.getString(blockEntityData, OWNERSHIP_TOKEN_KEY));
        }
        portable.putInt(FORMAT_VERSION_KEY,
                Math.max(1, Nbt.getInt(blockEntityData, FORMAT_VERSION_KEY)));
        if (Nbt.hasInt(blockEntityData, DATA_VERSION_KEY)) {
            portable.putInt(DATA_VERSION_KEY, Nbt.getInt(blockEntityData, DATA_VERSION_KEY));
        }
        if (Nbt.hasNumber(blockEntityData, TYPES_SUMMARY_KEY)) {
            portable.putInt(TYPES_SUMMARY_KEY,
                    Math.max(0, Nbt.getInt(blockEntityData, TYPES_SUMMARY_KEY)));
        }
        if (Nbt.hasNumber(blockEntityData, ITEMS_SUMMARY_KEY)) {
            portable.putLong(ITEMS_SUMMARY_KEY,
                    Math.max(0, Nbt.getLong(blockEntityData, ITEMS_SUMMARY_KEY)));
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
        return data == null ? 0 : Math.max(0, Nbt.getInt(data, CHESTS_KEY));
    }

    public static int storedTypes(ItemStack stack) {
        CompoundTag data = carried(stack);
        if (data == null) {
            return 0;
        }
        return Nbt.hasNumber(data, TYPES_SUMMARY_KEY)
                ? Math.max(0, Nbt.getInt(data, TYPES_SUMMARY_KEY))
                : Nbt.getCompoundList(data, ENTRIES_KEY).size();
    }

    public static long totalItems(ItemStack stack) {
        CompoundTag data = carried(stack);
        if (data == null) {
            return 0;
        }
        if (Nbt.hasNumber(data, ITEMS_SUMMARY_KEY)) {
            return Math.max(0, Nbt.getLong(data, ITEMS_SUMMARY_KEY));
        }
        long total = 0;
        var entries = Nbt.getCompoundList(data, ENTRIES_KEY);
        for (int index = 0; index < entries.size(); index++) {
            long count = Math.max(0, Nbt.getLong(Nbt.getCompound(entries, index), "Count"));
            total = Long.MAX_VALUE - total < count ? Long.MAX_VALUE : total + count;
        }
        return total;
    }
}
