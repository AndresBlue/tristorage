package com.andresblue.tristorage.storage;

import java.util.function.BooleanSupplier;

/** A lightweight reference to a shared, warm remote-storage session. */
public final class RemoteAccessHandle implements AutoCloseable {
    private final Runnable releaseAction;
    private final BooleanSupplier validity;
    private boolean closed;

    RemoteAccessHandle(Runnable releaseAction, BooleanSupplier validity) {
        this.releaseAction = releaseAction;
        this.validity = validity;
    }

    public boolean isValid() {
        return !closed && validity.getAsBoolean();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            releaseAction.run();
        }
    }
}
