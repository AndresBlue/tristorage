package com.andresblue.tristorage.storage;

public enum StorageTier {
    IRON(1, 27),
    DIAMOND(2, 27 * 16),
    BLAZE(3, 27 * 64),
    COSMIC(4, 27 * 512);

    public static final int TYPES_PER_CHEST = 27;
    public static final int ITEMS_PER_CHEST = 1_728;

    private final int level;
    private final int chestCapacity;

    StorageTier(int level, int chestCapacity) {
        this.level = level;
        this.chestCapacity = chestCapacity;
    }

    public int level() {
        return level;
    }

    public int chestCapacity() {
        return chestCapacity;
    }

    public int typeCapacity(int installedChests) {
        return Math.max(0, Math.min(installedChests, chestCapacity)) * TYPES_PER_CHEST;
    }

    public long itemCapacity(int installedChests) {
        return (long) Math.max(0, Math.min(installedChests, chestCapacity)) * ITEMS_PER_CHEST;
    }
}
