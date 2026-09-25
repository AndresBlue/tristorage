package com.andresblue.tristorage.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

class PortableCoreDataTest {
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
}
