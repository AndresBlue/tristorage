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

    @Test
    void recoveryKeepsTheRecordedTierOfALightlyFilledCore() {
        assertEquals(StorageTier.COSMIC, StorageTier.forRecovery("COSMIC", 3));
        assertEquals(StorageTier.DIAMOND, StorageTier.forRecovery("DIAMOND", 0));
    }

    @Test
    void recoveryFallsBackToTheSmallestFittingTier() {
        assertEquals(StorageTier.IRON, StorageTier.forRecovery("", 27));
        assertEquals(StorageTier.DIAMOND, StorageTier.forRecovery("", 28));
        assertEquals(StorageTier.BLAZE, StorageTier.forRecovery("UNKNOWN", 500));
        // A recorded tier that cannot hold the chests is never trusted.
        assertEquals(StorageTier.COSMIC, StorageTier.forRecovery("IRON", 2_000));
    }
}
