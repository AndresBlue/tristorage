package com.andresblue.tristorage.storage;

public final class RemoteDimensionPolicy {
    private RemoteDimensionPolicy() {}
    public static boolean canAccess(boolean sourceOverworld, boolean targetOverworld,
                                    boolean antenna) {
        return antenna || (sourceOverworld && targetOverworld);
    }
}
