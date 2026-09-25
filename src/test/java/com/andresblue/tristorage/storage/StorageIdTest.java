package com.andresblue.tristorage.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class StorageIdTest {
    @Test
    void deterministicMigrationIdentityIsStableAndOriginSensitive() {
        assertEquals(StorageId.deterministic("overworld:42:checksum"),
                StorageId.deterministic("overworld:42:checksum"));
        assertNotEquals(StorageId.deterministic("overworld:42:checksum"),
                StorageId.deterministic("the_nether:42:checksum"));
    }
}
