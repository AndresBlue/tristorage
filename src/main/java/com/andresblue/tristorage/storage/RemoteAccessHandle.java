package com.andresblue.tristorage.storage;

import java.util.function.BooleanSupplier;

/**
 * One open terminal's reference to a shared remote storage session.
 * Closing is idempotent so duplicate screen-close paths cannot underflow the
 * session's reference count or unload chunks still used by another player.
 */
public final class RemoteAccessHandle implements AutoCloseable {
    private final Runnable releaseAction;
    private final BooleanSupplier validity;
    private boolean closed;

    RemoteAccessHandle(Runnable releaseAction) {
        this(releaseAction, () -> true);
    }

    RemoteAccessHandle(Runnable releaseAction, BooleanSupplier validity) {
        this.releaseAction = releaseAction;
        this.validity = validity;
    }

    public boolean isValid() {
        return !closed && validity.getAsBoolean();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        releaseAction.run();
    }
}
