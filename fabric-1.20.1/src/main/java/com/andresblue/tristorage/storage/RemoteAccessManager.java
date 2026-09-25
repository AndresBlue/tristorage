package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates non-blocking remote opens. All players targeting the same
 * Linker share one chunk lease, one network scan and one warm session.
 */
public final class RemoteAccessManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("TriStorage/RemoteAccess");
    private static final Map<UUID, PendingRequest> PENDING = new HashMap<>();
    private static final Map<SessionKey, SharedSession> SESSIONS = new HashMap<>();
    private static boolean initialized;

    private RemoteAccessManager() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(RemoteAccessManager::expireRequestsAndSessions);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                cancelPending(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> cancelAll(server));
    }

    public static void request(ServerPlayer player, InteractionHand hand,
                               ServerLevel targetWorld, BlockPos linkerPos,
                               BlockPos coreHint, RemoteTerminalMode mode) {
        // Prepare creative category metadata before a cold repository begins
        // publishing entries, avoiding a second complete catalog pass later.
        TerminalFilter.prepareCreativeGroups(player);
        UUID playerId = player.getUUID();
        SessionKey key = new SessionKey(targetWorld, linkerPos.immutable());
        PendingRequest current = PENDING.get(playerId);
        if (current != null && current.matches(key, hand, mode)) {
            current.session().touch(player.getServer().getTickCount());
            StorageMetrics.increment("remote.inflight_request_hits");
            player.displayClientMessage(Component.translatable("message.tristorage.remote_loading"), true);
            return;
        }
        cancelPending(playerId);

        SharedSession candidate = getOrCreateSession(player.getServer(), key);
        StorageCoreBlockEntity readyCore = validateReadyCore(candidate);
        if (!isActive(candidate)) {
            candidate = getOrCreateSession(player.getServer(), key);
            readyCore = null;
        }
        SharedSession session = candidate;
        int now = player.getServer().getTickCount();
        session.touch(now);
        PendingRequest pending = new PendingRequest(
                player.getServer(), playerId, hand, mode, session, now,
                StorageMetrics.startTimer());
        PENDING.put(playerId, pending);
        StorageMetrics.increment("remote.requests");

        if (readyCore != null && readyCore.runtime().isReady()) {
            StorageMetrics.increment("remote.ready_core_hits");
            openTerminal(pending, readyCore);
            return;
        }

        if (readyCore == null && !session.loading()) {
            // A new/expired wireless session does not imply unloaded chunks.
            // Resolve an already-loaded network before creating chunk futures
            // or entering the staged scan pipeline. Tickets are still retained
            // below for the lifetime of the open screen, but no chunk is loaded
            // a second time and the UI can open in this same server task.
            StorageNetwork.LoadedSearch loaded = StorageNetwork.findCoreLoaded(
                    targetWorld, linkerPos);
            if (loaded.core() != null) {
                StorageMetrics.increment("remote.loaded_network_direct_hits");
                session.setLoading(true);
                retainCoreAndOpen(session, loaded.core());
                return;
            }
        }

        if (readyCore != null) {
            session.setCore(readyCore);
            session.setLoading(true);
        }

        if (!session.loading()) {
            session.setLoading(true);
            Set<ChunkPos> initialChunks = new HashSet<>();
            initialChunks.add(new ChunkPos(linkerPos));
            if (coreHint != null) {
                initialChunks.add(new ChunkPos(coreHint));
            }
            loadChunks(session, initialChunks, () -> scanNetwork(session));
        }
        // A fully loaded linker/core path completes synchronously above. Only
        // show the loading notice when the request genuinely remains pending.
        if (PENDING.get(playerId) == pending) {
            player.displayClientMessage(Component.translatable(
                    "message.tristorage.remote_loading"), true);
        }
    }

    private static SharedSession getOrCreateSession(MinecraftServer server, SessionKey key) {
        SharedSession existing = SESSIONS.get(key);
        if (existing != null && !existing.lease().isReleased()) {
            StorageMetrics.increment("remote.warm_session_hits");
            return existing;
        }
        evictOldestIdleSession(server);
        SharedSession created = new SharedSession(
                server, key, RemoteChunkLease.create(key.world()), server.getTickCount());
        SESSIONS.put(key, created);
        StorageMetrics.increment("remote.cold_sessions");
        return created;
    }

    private static void scanNetwork(SharedSession session) {
        if (!isActive(session)) {
            return;
        }
        session.touch(session.server().getTickCount());
        var linkerState = session.key().world().getBlockState(session.key().linkerPos());
        LinkerBlockEntity linker = LinkerBlock.getOrCreateLinkerEntity(
                session.key().world(), session.key().linkerPos(), linkerState);
        if (!(linkerState.getBlock() instanceof LinkerBlock) || linker == null) {
            failSession(session, "message.tristorage.remote_unavailable");
            return;
        }
        rejectPlayersWithoutDimensionalAccess(session, linker);
        if (!hasPendingPlayers(session) && session.openUsers() == 0) {
            invalidateSession(session);
            return;
        }

        StorageNetwork.LoadedSearch search = StorageNetwork.findCoreLoaded(
                session.key().world(), session.key().linkerPos());
        if (search.core() != null) {
            retainCoreAndOpen(session, search.core());
            return;
        }

        List<ChunkPos> missing = search.missingChunks().stream()
                .filter(chunk -> !session.lease().includes(chunk))
                .toList();
        if (missing.isEmpty()) {
            failSession(session, "message.tristorage.linker_offline");
            return;
        }
        loadChunks(session, missing, () -> scanNetwork(session));
    }

    private static void retainCoreAndOpen(SharedSession session,
                                          StorageCoreBlockEntity core) {
        loadChunks(session, anchorChunks(session.key().linkerPos(), core.getBlockPos()), () -> {
            if (!isActive(session) || !isValidCore(session, core)) {
                failSession(session, "message.tristorage.remote_unavailable");
                return;
            }
            session.setCore(core);
            session.setTopologyVersion(StorageNetwork.topologyVersion(session.key().world()));
            if (!core.runtime().isReady()) {
                session.setLoading(true);
                return;
            }
            session.setLoading(false);
            session.touch(session.server().getTickCount());
            openWaitingPlayers(session, core);
        });
    }

    /**
     * Chunks that must stay loaded for a remote session. A Linker usually sits
     * next to its Core, so both often share a chunk; {@code Set.of} rejected
     * that duplicate and every tablet open failed.
     */
    static List<ChunkPos> anchorChunks(BlockPos linkerPos, BlockPos corePos) {
        ChunkPos linkerChunk = new ChunkPos(linkerPos);
        ChunkPos coreChunk = new ChunkPos(corePos);
        return linkerChunk.equals(coreChunk)
                ? List.of(linkerChunk)
                : List.of(linkerChunk, coreChunk);
    }

    private static void openWaitingPlayers(SharedSession session,
                                           StorageCoreBlockEntity core) {
        List<PendingRequest> waiters = PENDING.values().stream()
                .filter(pending -> pending.session() == session)
                .toList();
        for (PendingRequest pending : waiters) {
            openTerminal(pending, core);
        }
    }

    private static void openTerminal(PendingRequest pending,
                                     StorageCoreBlockEntity core) {
        ServerPlayer player = eligiblePlayer(pending);
        if (player == null || !isValidCore(pending.session(), core)) {
            cancelPending(pending.playerId());
            return;
        }
        if (!hasDimensionalAccess(player, pending.session())) {
            cancelPending(pending.playerId());
            player.displayClientMessage(Component.translatable(
                    "message.tristorage.remote_requires_antenna"), true);
            return;
        }
        if (!PENDING.remove(pending.playerId(), pending)) {
            return;
        }

        SharedSession session = pending.session();
        RemoteAccessHandle handle = acquire(session, player, pending.mode());
        try {
            OptionalInt opened = player.openMenu(new SimpleMenuProvider(
                    (syncId, inventory, ignored) -> pending.mode() == RemoteTerminalMode.CRAFTING
                            ? new CraftingTerminalScreenHandler(
                            syncId, inventory, core, handle)
                            : new TerminalScreenHandler(syncId, inventory, core, handle),
                    Component.translatable(pending.mode() == RemoteTerminalMode.CRAFTING
                            ? "screen.tristorage.remote_crafting_terminal"
                            : "screen.tristorage.remote_terminal")
            ));
            if (opened.isEmpty()) {
                handle.close();
                finishPending(pending, "remote.failed_open");
                StorageMetrics.increment("remote.open_failures");
            } else {
                StorageMetrics.stopTimer("remote.open_total", pending.startedAtNanos());
                StorageMetrics.increment("remote.opens");
            }
        } catch (RuntimeException exception) {
            handle.close();
            finishPending(pending, "remote.failed_open");
            StorageMetrics.increment("remote.open_failures");
            player.displayClientMessage(Component.translatable("message.tristorage.remote_unavailable"), true);
            LOGGER.error("Could not open remote storage for {}",
                    player.getGameProfile().getName(), exception);
        }
    }

    private static RemoteAccessHandle acquire(SharedSession session,
                                              ServerPlayer player,
                                              RemoteTerminalMode mode) {
        session.incrementOpenUsers();
        session.touch(session.server().getTickCount());
        return new RemoteAccessHandle(
                () -> release(session),
                () -> isValidOpenSession(session, player, mode));
    }

    private static void release(SharedSession session) {
        session.decrementOpenUsers();
        session.touch(session.server().getTickCount());
    }

    private static void loadChunks(SharedSession session,
                                   Collection<ChunkPos> requested,
                                   Runnable continuation) {
        if (!isActive(session)) {
            return;
        }
        List<ChunkPos> newChunks = requested.stream()
                .filter(chunk -> !session.lease().includes(chunk))
                .distinct()
                .toList();
        if (!RemoteLoadPolicy.withinChunkBudget(
                session.lease().chunkCount(), newChunks.size())) {
            failSession(session, "message.tristorage.remote_too_large");
            return;
        }

        List<CompletableFuture<Boolean>> loads = new ArrayList<>(requested.size());
        long loadStarted = StorageMetrics.startTimer();
        StorageMetrics.add("remote.chunk_batches_requested", 1);
        StorageMetrics.add("remote.chunks_requested", newChunks.size());
        try {
            for (ChunkPos chunk : requested) {
                loads.add(session.lease().includeAsync(chunk));
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Could not schedule a remote TriStorage chunk load", exception);
            failSession(session, "message.tristorage.remote_unavailable");
            return;
        }
        session.touch(session.server().getTickCount());
        if (loads.stream().allMatch(CompletableFuture::isDone)) {
            StorageMetrics.stopTimer("remote.chunk_batch_load", loadStarted);
            StorageMetrics.increment("remote.loaded_chunk_fast_path_batches");
            if (loads.stream().allMatch(RemoteAccessManager::loadedSuccessfully)) {
                continuation.run();
            } else {
                failSession(session, "message.tristorage.remote_unavailable");
            }
            return;
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> session.server().execute(() -> {
                    StorageMetrics.stopTimer("remote.chunk_batch_load", loadStarted);
                    if (!isActive(session)) {
                        return;
                    }
                    boolean loaded = error == null && loads.stream()
                            .allMatch(load -> load.getNow(false));
                    if (!loaded) {
                        failSession(session, "message.tristorage.remote_unavailable");
                        return;
                    }
                    continuation.run();
                }));
    }

    private static boolean loadedSuccessfully(CompletableFuture<Boolean> load) {
        return load.isDone() && !load.isCancelled() && !load.isCompletedExceptionally()
                && Boolean.TRUE.equals(load.getNow(false));
    }

    private static StorageCoreBlockEntity validateReadyCore(SharedSession session) {
        StorageCoreBlockEntity core = session.core();
        if (core == null) {
            return null;
        }
        if (isValidCore(session, core)
                && session.topologyVersion() == StorageNetwork.topologyVersion(
                        session.key().world())) {
            return core;
        }
        if (isValidCore(session, core)) {
            StorageCoreBlockEntity resolved = StorageNetwork.findCoreLoaded(
                    session.key().world(), session.key().linkerPos()).core();
            if (resolved == core) {
                session.setTopologyVersion(StorageNetwork.topologyVersion(
                        session.key().world()));
                return core;
            }
        }
        invalidateSession(session);
        return null;
    }

    private static boolean isValidCore(SharedSession session,
                                       StorageCoreBlockEntity core) {
        return isActive(session)
                && !core.isRemoved()
                && !core.isRecoveryRequired()
                && core.getLevel() == session.key().world()
                && session.key().world().getBlockEntity(core.getBlockPos()) == core
                && session.key().world().getBlockState(session.key().linkerPos()).getBlock()
                instanceof LinkerBlock
                && session.key().world().getBlockEntity(session.key().linkerPos())
                instanceof LinkerBlockEntity;
    }

    private static void rejectPlayersWithoutDimensionalAccess(
            SharedSession session, LinkerBlockEntity linker) {
        List<PendingRequest> rejected = PENDING.values().stream()
                .filter(pending -> pending.session() == session)
                .filter(pending -> {
                    ServerPlayer player = pending.server().getPlayerList()
                            .getPlayer(pending.playerId());
                    return player == null || !hasDimensionalAccess(player, session, linker);
                })
                .toList();
        for (PendingRequest pending : rejected) {
            if (PENDING.remove(pending.playerId(), pending)) {
                finishPending(pending, "remote.dimension_rejected_open");
                StorageMetrics.increment("remote.dimension_rejected_requests");
                ServerPlayer player = pending.server().getPlayerList()
                        .getPlayer(pending.playerId());
                if (player != null) {
                    player.displayClientMessage(Component.translatable(
                            "message.tristorage.remote_requires_antenna"), true);
                }
            }
        }
    }

    private static boolean isValidOpenSession(SharedSession session,
                                              ServerPlayer player,
                                              RemoteTerminalMode mode) {
        return isActive(session) && player.isAlive()
                && hasDimensionalAccess(player, session)
                && hasLinkedTablet(player, session, mode);
    }

    private static boolean hasLinkedTablet(ServerPlayer player,
                                           SharedSession session,
                                           RemoteTerminalMode mode) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (RemoteTabletItem.isLinkedTo(player.getInventory().getItem(slot),
                    session.key().world().dimension(), session.key().linkerPos(), mode)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDimensionalAccess(ServerPlayer player,
                                                SharedSession session) {
        return session.key().world().getBlockEntity(session.key().linkerPos())
                instanceof LinkerBlockEntity linker
                && hasDimensionalAccess(player, session, linker);
    }

    private static boolean hasDimensionalAccess(ServerPlayer player,
                                                SharedSession session,
                                                LinkerBlockEntity linker) {
        return RemoteDimensionPolicy.canAccess(
                player.level().dimension().equals(Level.OVERWORLD),
                session.key().world().dimension().equals(Level.OVERWORLD),
                linker.isAntennaActive());
    }

    private static ServerPlayer eligiblePlayer(PendingRequest pending) {
        if (PENDING.get(pending.playerId()) != pending || !isActive(pending.session())) {
            return null;
        }
        ServerPlayer player = pending.server().getPlayerList()
                .getPlayer(pending.playerId());
        if (player == null || player.containerMenu != player.inventoryMenu) {
            return null;
        }
        SessionKey key = pending.session().key();
        return RemoteTabletItem.isLinkedTo(
                player.getItemInHand(pending.hand()),
                key.world().dimension(), key.linkerPos(), pending.mode()) ? player : null;
    }

    private static boolean isActive(SharedSession session) {
        return SESSIONS.get(session.key()) == session && !session.lease().isReleased();
    }

    private static void expireRequestsAndSessions(MinecraftServer server) {
        List<SharedSession> repositoriesReady = SESSIONS.values().stream()
                .filter(session -> session.server() == server)
                .filter(SharedSession::loading)
                .filter(session -> session.core() != null)
                .filter(session -> session.core().runtime().isReady())
                .toList();
        for (SharedSession session : repositoriesReady) {
            session.setLoading(false);
            session.touch(server.getTickCount());
            openWaitingPlayers(session, session.core());
        }

        List<PendingRequest> abandoned = PENDING.values().stream()
                .filter(pending -> pending.server() == server)
                .filter(pending -> eligiblePlayer(pending) == null)
                .toList();
        abandoned.forEach(pending -> cancelPending(pending.playerId()));

        List<PendingRequest> expired = PENDING.values().stream()
                .filter(pending -> pending.server() == server)
                .filter(pending -> RemoteLoadPolicy.hasTimedOut(
                        pending.startedAtTick(), server.getTickCount()))
                .toList();
        for (PendingRequest pending : expired) {
            if (PENDING.remove(pending.playerId(), pending)) {
                finishPending(pending, "remote.timed_out_open");
                StorageMetrics.increment("remote.request_timeouts");
                ServerPlayer player = server.getPlayerList()
                        .getPlayer(pending.playerId());
                if (player != null) {
                    player.displayClientMessage(Component.translatable(
                            "message.tristorage.remote_timeout"), true);
                }
            }
        }

        List<SharedSession> cold = SESSIONS.values().stream()
                .filter(session -> session.server() == server)
                .filter(session -> session.openUsers() == 0)
                .filter(session -> !hasPendingPlayers(session))
                .filter(session -> RemoteLoadPolicy.warmSessionExpired(
                        session.lastUsedTick(), server.getTickCount()))
                .toList();
        cold.forEach(RemoteAccessManager::invalidateSession);
        enforceWarmChunkBudget(server);
    }

    private static boolean hasPendingPlayers(SharedSession session) {
        return PENDING.values().stream().anyMatch(pending -> pending.session() == session);
    }

    private static void failSession(SharedSession session, String translationKey) {
        if (!SESSIONS.remove(session.key(), session)) {
            return;
        }
        session.lease().release();
        List<PendingRequest> failed = PENDING.values().stream()
                .filter(pending -> pending.session() == session)
                .toList();
        for (PendingRequest pending : failed) {
            if (PENDING.remove(pending.playerId(), pending)) {
                finishPending(pending, "remote.failed_open");
                StorageMetrics.increment("remote.failed_requests");
                ServerPlayer player = pending.server().getPlayerList()
                        .getPlayer(pending.playerId());
                if (player != null) {
                    player.displayClientMessage(Component.translatable(translationKey), true);
                }
            }
        }
    }

    private static void cancelPending(UUID playerId) {
        PendingRequest pending = PENDING.remove(playerId);
        if (pending != null) {
            pending.session().touch(pending.server().getTickCount());
            finishPending(pending, "remote.cancelled_open");
            StorageMetrics.increment("remote.cancelled_requests");
        }
    }

    private static void finishPending(PendingRequest pending, String timerName) {
        StorageMetrics.stopTimer(timerName, pending.startedAtNanos());
    }

    private static void invalidateSession(SharedSession session) {
        if (SESSIONS.remove(session.key(), session)) {
            StorageMetrics.add("remote.warm_chunks_released", session.lease().chunkCount());
            StorageMetrics.increment("remote.sessions_invalidated");
            session.lease().release();
        }
    }

    private static void enforceWarmChunkBudget(MinecraftServer server) {
        while (true) {
            List<SharedSession> idle = SESSIONS.values().stream()
                    .filter(session -> session.server() == server)
                    .filter(session -> session.openUsers() == 0)
                    .filter(session -> !hasPendingPlayers(session))
                    .filter(session -> session.lease().chunkCount() > 0)
                    .sorted(Comparator.comparingInt(SharedSession::lastUsedTick))
                    .toList();
            int chunks = idle.stream().mapToInt(session -> session.lease().chunkCount()).sum();
            if (RemoteLoadPolicy.withinIdleWarmChunkBudget(chunks) || idle.isEmpty()) {
                return;
            }
            StorageMetrics.increment("remote.sessions_evicted_for_chunk_budget");
            invalidateSession(idle.get(0));
        }
    }

    private static void evictOldestIdleSession(MinecraftServer server) {
        long count = SESSIONS.values().stream()
                .filter(session -> session.server() == server)
                .count();
        if (count < RemoteLoadPolicy.MAX_SHARED_SESSIONS) {
            return;
        }
        SESSIONS.values().stream()
                .filter(session -> session.server() == server)
                .filter(session -> session.openUsers() == 0)
                .filter(session -> !hasPendingPlayers(session))
                .min(Comparator.comparingInt(SharedSession::lastUsedTick))
                .ifPresent(RemoteAccessManager::invalidateSession);
    }

    private static void cancelAll(MinecraftServer server) {
        List<PendingRequest> pending = PENDING.values().stream()
                .filter(request -> request.server() == server)
                .toList();
        for (PendingRequest request : pending) {
            if (PENDING.remove(request.playerId(), request)) {
                finishPending(request, "remote.server_stop_open");
            }
        }
        List<SharedSession> sessions = SESSIONS.values().stream()
                .filter(session -> session.server() == server)
                .toList();
        sessions.forEach(RemoteAccessManager::invalidateSession);
    }

    private record SessionKey(ServerLevel world, BlockPos linkerPos) {
    }

    private record PendingRequest(MinecraftServer server, UUID playerId, InteractionHand hand,
                                  RemoteTerminalMode mode,
                                  SharedSession session, int startedAtTick,
                                  long startedAtNanos) {
        private boolean matches(SessionKey candidateKey, InteractionHand candidateHand,
                                RemoteTerminalMode candidateMode) {
            return session.key().equals(candidateKey) && hand == candidateHand
                    && mode == candidateMode;
        }
    }

    private static final class SharedSession {
        private final MinecraftServer server;
        private final SessionKey key;
        private final RemoteChunkLease lease;
        private int lastUsedTick;
        private int openUsers;
        private boolean loading;
        private StorageCoreBlockEntity core;
        private long topologyVersion = Long.MIN_VALUE;

        private SharedSession(MinecraftServer server, SessionKey key,
                              RemoteChunkLease lease, int lastUsedTick) {
            this.server = server;
            this.key = key;
            this.lease = lease;
            this.lastUsedTick = lastUsedTick;
        }

        private MinecraftServer server() {
            return server;
        }

        private SessionKey key() {
            return key;
        }

        private RemoteChunkLease lease() {
            return lease;
        }

        private int lastUsedTick() {
            return lastUsedTick;
        }

        private void touch(int tick) {
            lastUsedTick = tick;
        }

        private int openUsers() {
            return openUsers;
        }

        private void incrementOpenUsers() {
            openUsers++;
        }

        private void decrementOpenUsers() {
            if (openUsers > 0) {
                openUsers--;
            }
        }

        private boolean loading() {
            return loading;
        }

        private void setLoading(boolean loading) {
            this.loading = loading;
        }

        private StorageCoreBlockEntity core() {
            return core;
        }

        private void setCore(StorageCoreBlockEntity core) {
            this.core = core;
        }

        private long topologyVersion() {
            return topologyVersion;
        }

        private void setTopologyVersion(long topologyVersion) {
            this.topologyVersion = topologyVersion;
        }
    }
}
