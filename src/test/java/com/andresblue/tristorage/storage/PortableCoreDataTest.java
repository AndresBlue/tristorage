package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.TestStacks;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

class PortableCoreDataTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sanitizedCopyKeepsOnlyStoragePayload() {
        CompoundTag raw = new CompoundTag();
        raw.putString("id", "tristorage:storage_core");
        raw.putInt("x", 12);
        raw.putInt(PortableCoreData.CHESTS_KEY, 7);
        ListTag entries = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putLong("Count", 123L);
        entries.add(entry);
        raw.put(PortableCoreData.ENTRIES_KEY, entries);

        CompoundTag portable = PortableCoreData.sanitizedCopy(raw);

        assertEquals(7, portable.getInt(PortableCoreData.CHESTS_KEY));
        assertEquals(123L, portable.getList(
                PortableCoreData.ENTRIES_KEY, CompoundTag.TAG_COMPOUND)
                .getCompound(0).getLong("Count"));
        assertFalse(portable.contains("id"));
        assertFalse(portable.contains("x"));
        assertNotSame(entries, portable.get(PortableCoreData.ENTRIES_KEY));
    }

    @Test
    void sanitizedCopyRejectsNegativeChestCountsAndUnrelatedData() {
        CompoundTag raw = new CompoundTag();
        raw.putInt(PortableCoreData.CHESTS_KEY, -42);
        raw.putString("Injected", "not portable");

        CompoundTag portable = PortableCoreData.sanitizedCopy(raw);

        assertEquals(0, portable.getInt(PortableCoreData.CHESTS_KEY));
        assertFalse(portable.contains("Injected"));
        assertTrue(portable.contains(PortableCoreData.CHESTS_KEY));
    }

    @Test
    void sanitizedCopyKeepsTheDataVersionOfTheEntries() {
        CompoundTag raw = new CompoundTag();
        raw.putInt(PortableCoreData.DATA_VERSION_KEY, 3465);

        assertEquals(3465, PortableCoreData.sanitizedCopy(raw)
                .getInt(PortableCoreData.DATA_VERSION_KEY));
    }

    private static CompoundTag storage(int chests, long... counts) {
        CompoundTag storage = new CompoundTag();
        storage.putInt(PortableCoreData.CHESTS_KEY, chests);
        ListTag entries = new ListTag();
        for (long count : counts) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Count", count);
            entries.add(entry);
        }
        storage.put(PortableCoreData.ENTRIES_KEY, entries);
        return storage;
    }

    @Test
    void readsCoresFromTheNeoForgePortPayload() {
        // TriStorage 1.1 for NeoForge 1.21.1 kept the storage under TriStorageCore.
        ItemStack core = new ItemStack(Items.IRON_BLOCK);
        TestStacks.edit(core, tag -> tag.put("TriStorageCore", storage(3, 10, 5)));

        assertEquals(3, PortableCoreData.installedChests(core));
        assertEquals(2, PortableCoreData.storedTypes(core));
        assertEquals(15, PortableCoreData.totalItems(core));
        assertNotNull(PortableCoreData.copyFrom(core));
    }

    @Test
    void readsCoresFromTheFirstNeoForgePortLayout() {
        // TriStorage 1.0 for NeoForge 1.21.1 kept it at the root of custom data.
        ItemStack core = new ItemStack(Items.IRON_BLOCK);
        TestStacks.edit(core, tag -> tag.merge(storage(1, 7)));

        assertEquals(1, PortableCoreData.installedChests(core));
        assertEquals(7, PortableCoreData.totalItems(core));
    }

    @Test
    void unrelatedCustomDataCarriesNoStorage() {
        ItemStack core = new ItemStack(Items.IRON_BLOCK);
        TestStacks.edit(core, tag -> tag.putString("Note", "hello"));

        assertEquals(0, PortableCoreData.installedChests(core));
        assertNull(PortableCoreData.copyFrom(core));
    }
}
