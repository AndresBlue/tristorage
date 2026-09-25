package com.andresblue.tristorage.platform;

/** Holds the loader's {@link ClientPlatformHooks}. Installed by the client entrypoint. */
public final class ClientPlatform {
    private static ClientPlatformHooks hooks;

    private ClientPlatform() {
    }

    public static void install(ClientPlatformHooks installed) {
        hooks = installed;
    }

    public static ClientPlatformHooks get() {
        if (hooks == null) {
            throw new IllegalStateException("TriStorage client platform hooks are not installed");
        }
        return hooks;
    }
}
