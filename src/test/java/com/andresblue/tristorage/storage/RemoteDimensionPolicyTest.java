package com.andresblue.tristorage.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteDimensionPolicyTest {
    @Test
    void basicLinkerOnlyWorksEntirelyInsideOverworld() {
        assertTrue(RemoteDimensionPolicy.canAccess(true, true, false));
        assertFalse(RemoteDimensionPolicy.canAccess(false, true, false));
        assertFalse(RemoteDimensionPolicy.canAccess(true, false, false));
        assertFalse(RemoteDimensionPolicy.canAccess(false, false, false));
    }

    @Test
    void activeAntennaAllowsEveryDimensionCombination() {
        assertTrue(RemoteDimensionPolicy.canAccess(true, true, true));
        assertTrue(RemoteDimensionPolicy.canAccess(false, true, true));
        assertTrue(RemoteDimensionPolicy.canAccess(true, false, true));
        assertTrue(RemoteDimensionPolicy.canAccess(false, false, true));
    }
}
