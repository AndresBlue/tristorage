package com.andresblue.tristorage.platform;

/**
 * Registers content into one registry. Fabric registers immediately; Forge
 * registers during its RegisterEvent, the only time its registries accept
 * new entries.
 */
public interface Registrar<T> {
    <V extends T> V register(String path, V value);
}
