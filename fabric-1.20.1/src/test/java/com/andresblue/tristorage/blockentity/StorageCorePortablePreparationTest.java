package com.andresblue.tristorage.blockentity;

import com.andresblue.tristorage.storage.StorageRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageCorePortablePreparationTest {
    @Test
    void pendingDurabilityBarrierDoesNotEnterRecoveryMode() {
        StorageCoreBlockEntity.PortableDecision decision =
                StorageCoreBlockEntity.interpretPortablePreparation(
                        StorageRepository.PortablePreparation.pending());

        assertFalse(decision.prepared());
        assertFalse(decision.recoveryRequired());
    }

    @Test
    void invalidOwnershipStillEntersRecoveryMode() {
        StorageCoreBlockEntity.PortableDecision decision =
                StorageCoreBlockEntity.interpretPortablePreparation(
                        StorageRepository.PortablePreparation.invalid());

        assertFalse(decision.prepared());
        assertTrue(decision.recoveryRequired());
    }

    @Test
    void durablePreparationCarriesTheRotatedToken() {
        StorageCoreBlockEntity.PortableDecision decision =
                StorageCoreBlockEntity.interpretPortablePreparation(
                        StorageRepository.PortablePreparation.ready("rotated"));

        assertTrue(decision.prepared());
        assertFalse(decision.recoveryRequired());
        assertEquals("rotated", decision.token());
    }
}
