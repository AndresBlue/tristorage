package com.andresblue.tristorage.storage;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** Stable identity of one logical storage, independent from its Core chunk. */
public record StorageId(UUID value) {
    public static StorageId random() {
        return new StorageId(UUID.randomUUID());
    }

    public static StorageId deterministic(String origin) {
        return new StorageId(UUID.nameUUIDFromBytes(
                origin.getBytes(StandardCharsets.UTF_8)));
    }

    public static StorageId parse(String value) {
        return new StorageId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
