package com.andresblue.tristorage.storage;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RemoteAccessHandleTest {
    @Test
    void releasesSharedSessionOnlyOnce() {
        AtomicInteger releases = new AtomicInteger();
        RemoteAccessHandle handle = new RemoteAccessHandle(releases::incrementAndGet);

        handle.close();
        handle.close();

        assertEquals(1, releases.get());
    }

    @Test
    void exposesLiveDimensionalValidityAndClosesPermanently() {
        AtomicInteger valid = new AtomicInteger(1);
        RemoteAccessHandle handle = new RemoteAccessHandle(
                () -> { }, () -> valid.get() == 1);

        valid.set(0);
        assertFalse(handle.isValid());
        handle.close();
        valid.set(1);
        assertFalse(handle.isValid());
    }
}
