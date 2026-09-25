package com.andresblue.tristorage.storage;

/** Pure limits for remote access; separated so boundary behavior is testable. */
final class RemoteLoadPolicy {
    static final int MAX_CHUNKS_PER_REQUEST = 8;
    static final int MAX_SHARED_SESSIONS = 16;
    static final int REQUEST_TIMEOUT_TICKS = 20 * 30;
    static final int WARM_SESSION_TICKS = 20 * 30;

    private RemoteLoadPolicy() {
    }

    static boolean withinChunkBudget(int alreadyIncluded, int additional) {
        return alreadyIncluded >= 0 && additional >= 0
                && alreadyIncluded + additional <= MAX_CHUNKS_PER_REQUEST;
    }

    static boolean hasTimedOut(int startedAtTick, int currentTick) {
        return currentTick - startedAtTick >= REQUEST_TIMEOUT_TICKS;
    }

    static boolean warmSessionExpired(int lastUsedTick, int currentTick) {
        return currentTick - lastUsedTick >= WARM_SESSION_TICKS;
    }
}
