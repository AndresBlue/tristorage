package com.andresblue.tristorage.storage;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableCoreDataTest {
    @Test
    void sanitizedCopyKeepsOnlyStoragePayload() {
        NbtCompound raw = new NbtCompound();
        raw.putString("id", "tristorage:storage_core");
        raw.putInt("x", 12);
        raw.putInt(PortableCoreData.CHESTS_KEY, 7);
        NbtList entries = new NbtList();
        NbtCompound entry = new NbtCompound();
        entry.putLong("Count", 123L);
        entries.add(entry);
        raw.put(PortableCoreData.ENTRIES_KEY, entries);

        NbtCompound portable = PortableCoreData.sanitizedCopy(raw);

        assertEquals(7, portable.getInt(PortableCoreData.CHESTS_KEY));
        assertEquals(123L, portable.getList(
                PortableCoreData.ENTRIES_KEY, NbtCompound.COMPOUND_TYPE)
                .getCompound(0).getLong("Count"));
        assertFalse(portable.contains("id"));
        assertFalse(portable.contains("x"));
        assertNotSame(entries, portable.get(PortableCoreData.ENTRIES_KEY));
    }

    @Test
    void sanitizedCopyRejectsNegativeChestCountsAndUnrelatedData() {
        NbtCompound raw = new NbtCompound();
        raw.putInt(PortableCoreData.CHESTS_KEY, -42);
        raw.putString("Injected", "not portable");

        NbtCompound portable = PortableCoreData.sanitizedCopy(raw);

        assertEquals(0, portable.getInt(PortableCoreData.CHESTS_KEY));
        assertFalse(portable.contains("Injected"));
        assertTrue(portable.contains(PortableCoreData.CHESTS_KEY));
    }
}
