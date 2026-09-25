package com.andresblue.tristorage.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageTierTest {
    @Test
    void tierChestCapsMatchTheDesign() {
        assertEquals(27, StorageTier.IRON.chestCapacity());
        assertEquals(432, StorageTier.DIAMOND.chestCapacity());
        assertEquals(1_728, StorageTier.BLAZE.chestCapacity());
        assertEquals(13_824, StorageTier.COSMIC.chestCapacity());
    }

    @Test
    void everyChestAddsExactCapacity() {
        assertEquals(81, StorageTier.IRON.typeCapacity(3));
        assertEquals(5_184, StorageTier.IRON.itemCapacity(3));
        assertEquals(23_887_872L, StorageTier.COSMIC.itemCapacity(13_824));
    }
}
