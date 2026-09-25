package com.andresblue.tristorage.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteLoadPolicyTest {
    @Test
    void capsOneRemoteRequestAtEightChunks() {
        assertTrue(RemoteLoadPolicy.withinChunkBudget(1, 7));
        assertFalse(RemoteLoadPolicy.withinChunkBudget(1, 8));
        assertFalse(RemoteLoadPolicy.withinChunkBudget(-1, 1));
    }

    @Test
    void expiresAtThirtySecondsWithoutBreakingAcrossIntegerWrap() {
        int start = Integer.MAX_VALUE - 200;
        assertFalse(RemoteLoadPolicy.hasTimedOut(start, start + 599));
        assertTrue(RemoteLoadPolicy.hasTimedOut(start, start + 600));
    }

    @Test
    void keepsSharedRemoteSessionWarmForThirtySeconds() {
        assertFalse(RemoteLoadPolicy.warmSessionExpired(100, 699));
        assertTrue(RemoteLoadPolicy.warmSessionExpired(100, 700));
    }
}
