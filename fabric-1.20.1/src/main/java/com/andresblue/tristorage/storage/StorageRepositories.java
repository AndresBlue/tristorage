package com.andresblue.tristorage.storage;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;

import java.util.IdentityHashMap;
import java.util.Map;

/** Server lifecycle owner for world-local TriStorage repositories. */
public final class StorageRepositories {
    private static final Map<MinecraftServer, StorageRepository> REPOSITORIES =
            new IdentityHashMap<>();
    private static boolean initialized;

    private StorageRepositories() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        // World LOAD precedes normal chunk/block-entity loading. Preparing the
        // creative catalog here means a large runtime is indexed with the final
        // category generation immediately, instead of paying an O(types)
        // recategorization on the first terminal/tablet open.
        ServerWorldEvents.LOAD.register((server, world) ->
                TerminalFilter.prepareCreativeGroups(world));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            get(server);
            // Build creative-tab metadata during server startup rather than on
            // the first tablet click. In large packs updateDisplayContext can
            // take hundreds of milliseconds, but its result is stable until a
            // data-pack reload.
            TerminalFilter.prepareCreativeGroups(server.getOverworld());
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
                (server, resourceManager, success) -> {
                    if (success) {
                        TerminalFilter.invalidateCreativeGroups();
                        TerminalFilter.prepareCreativeGroups(server.getOverworld());
                    }
                });
        ServerTickEvents.END_SERVER_TICK.register(server -> get(server).tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            StorageTickCoordinator.flush();
            StorageRepository repository = REPOSITORIES.remove(server);
            if (repository != null) {
                repository.closeAndFlush();
            }
        });
    }

    public static StorageRepository get(MinecraftServer server) {
        return REPOSITORIES.computeIfAbsent(server, StorageRepository::new);
    }
}
