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
    void keepsSharedRemoteSessionWarmForFiveMinutes() {
        assertFalse(RemoteLoadPolicy.warmSessionExpired(100, 6_099));
        assertTrue(RemoteLoadPolicy.warmSessionExpired(100, 6_100));
    }

    @Test
    void capsIdleWarmSessionsAtSixteenChunks() {
        assertTrue(RemoteLoadPolicy.withinIdleWarmChunkBudget(16));
        assertFalse(RemoteLoadPolicy.withinIdleWarmChunkBudget(17));
        assertFalse(RemoteLoadPolicy.withinIdleWarmChunkBudget(-1));
    }
}
