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

    /** Smallest tier that can hold {@code installedChests}. */
    public static StorageTier smallestFor(int installedChests) {
        for (StorageTier tier : values()) {
            if (installedChests <= tier.chestCapacity) {
                return tier;
            }
        }
        return COSMIC;
    }

    /**
     * Tier for an operator recovery: the recorded tier when it still fits the
     * installed chests, otherwise the smallest tier that does. Manifests from
     * before tiers were recorded have no name and fall back as well.
     */
    public static StorageTier forRecovery(String recorded, int installedChests) {
        StorageTier fallback = smallestFor(installedChests);
        if (recorded == null || recorded.isEmpty()) {
            return fallback;
        }
        try {
            StorageTier tier = valueOf(recorded);
            return tier.chestCapacity >= installedChests ? tier : fallback;
        } catch (IllegalArgumentException unknownTier) {
            return fallback;
        }
    }
}
