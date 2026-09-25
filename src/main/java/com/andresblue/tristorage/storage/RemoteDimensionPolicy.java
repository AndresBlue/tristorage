package com.andresblue.tristorage.storage;

/** Pure access rules for the Linker's dimensional antenna upgrade. */
public final class RemoteDimensionPolicy {
    private RemoteDimensionPolicy() {
    }

    public static boolean canAccess(boolean sourceIsOverworld,
                                    boolean targetIsOverworld,
                                    boolean activeAntenna) {
        return activeAntenna || (sourceIsOverworld && targetIsOverworld);
    }
}
