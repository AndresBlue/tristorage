package com.andresblue.tristorage.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.List;

/** Portable storage payload used by dropped/upgraded cores. */
public record CoreStorageData(int installedChests, List<StoredStack> entries) {
    public CoreStorageData {
        installedChests = Math.max(0, installedChests);
        entries = List.copyOf(entries);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("InstalledChests", installedChests);
        ListTag list = new ListTag();
        for (StoredStack entry : entries) {
            CompoundTag stored = new CompoundTag();
            stored.put("Stack", entry.stack().save(registries));
            stored.putLong("Count", entry.count());
            list.add(stored);
        }
        tag.put("Entries", list);
    }

    public static CoreStorageData load(CompoundTag tag, HolderLookup.Provider registries) {
        int chests = Math.max(0, tag.getInt("InstalledChests"));
        List<StoredStack> entries = new ArrayList<>();
        ListTag list = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag stored = list.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, stored.getCompound("Stack"));
            long count = Math.max(0L, stored.getLong("Count"));
            if (!stack.isEmpty() && count > 0L) {
                entries.add(new StoredStack(stack.copyWithCount(1), count));
            }
        }
        return new CoreStorageData(chests, entries);
    }

    public record StoredStack(ItemStack stack, long count) {
        public StoredStack {
            stack = stack.copyWithCount(1);
            count = Math.max(0L, count);
        }
    }
}
