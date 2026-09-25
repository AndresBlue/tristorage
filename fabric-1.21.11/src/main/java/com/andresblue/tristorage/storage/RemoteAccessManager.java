package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
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
 * Coordinates asynchronous remote access. Concurrent users of one Linker
 * share a single load operation and a short warm lease, eliminating duplicate
 * chunk work when tablets are opened together or repeatedly.
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
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> cancelPending(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(RemoteAccessManager::cancelAll);
    }

    public static void request(ServerPlayerEntity player, Hand hand, ServerWorld targetWorld,
                               BlockPos linkerPos, BlockPos coreHint) {
        UUID playerId = player.getUuid();
        SessionKey key = new SessionKey(targetWorld, linkerPos.toImmutable());
        PendingRequest current = PENDING.get(playerId);
        if (current != null && current.matches(key, hand)) {
            current.session().touch(player.getEntityWorld().getServer().getTicks());
            player.sendMessage(Text.translatable("message.tristorage.remote_loading"), true);
            return;
        }

        cancelPending(playerId);
        SharedSession candidate = getOrCreateSession(player.getEntityWorld().getServer(), key);
        if (candidate == null) {
            player.sendMessage(Text.translatable("message.tristorage.remote_unavailable"), true);
            return;
        }
        StorageCoreBlockEntity readyCore = validateReadyCore(candidate);
        if (!isActive(candidate)) {
            candidate = getOrCreateSession(player.getEntityWorld().getServer(), key);
            readyCore = null;
        }
        if (candidate == null) {
            player.sendMessage(Text.translatable("message.tristorage.remote_unavailable"), true);
            return;
        }

        SharedSession session = candidate;
        int now = session.server().getTicks();
        session.touch(now);
        PendingRequest pending = new PendingRequest(session.server(), playerId, hand, session, now);
        PENDING.put(playerId, pending);
        if (readyCore != null) {
            openTerminal(pending, readyCore);
            return;
        }

        player.sendMessage(Text.translatable("message.tristorage.remote_loading"), true);
        if (!session.loading()) {
            session.setLoading(true);
            Set<ChunkPos> initialChunks = new HashSet<>();
            initialChunks.add(new ChunkPos(linkerPos));
            if (coreHint != null
                    && player.getEntityWorld().getRegistryKey().equals(World.OVERWORLD)
                    && targetWorld.getRegistryKey().equals(World.OVERWORLD)) {
                initialChunks.add(new ChunkPos(coreHint));
            }
            loadChunks(session, initialChunks, () -> scanNetwork(session));
        }
    }

    private static SharedSession getOrCreateSession(MinecraftServer server, SessionKey key) {
        SharedSession existing = SESSIONS.get(key);
        if (existing != null && !existing.lease().isReleased()) {
            return existing;
        }
        evictOldestIdleSession(server);
        long count = SESSIONS.values().stream().filter(session -> session.server() == server).count();
        if (count >= RemoteLoadPolicy.MAX_SHARED_SESSIONS) {
            return null;
        }
        SharedSession created = new SharedSession(
                server, key, RemoteChunkLease.create(key.world()), server.getTicks());
        SESSIONS.put(key, created);
        return created;
    }

    private static void scanNetwork(SharedSession session) {
        if (!isActive(session)) {
            return;
        }
        session.touch(session.server().getTicks());
        if (!(session.key().world().getBlockState(session.key().linkerPos()).getBlock()
                instanceof LinkerBlock)
                || !(session.key().world().getBlockEntity(session.key().linkerPos())
                instanceof LinkerBlockEntity linker)) {
            failSession(session, "message.tristorage.remote_unavailable");
            return;
        }
        rejectPlayersWithoutDimensionalAccess(session, linker);
        if (!hasPendingPlayers(session) && session.openUsers() == 0) {
            invalidateSession(session);
            return;
        }

        StorageNetwork.LoadedSearch search =
                StorageNetwork.findCoreLoaded(session.key().world(), session.key().linkerPos());
        if (search.core() != null) {
            retainCoreAndOpen(session, search.core());
            return;
        }
        List<ChunkPos> missing = search.missingChunks().stream()
                .filter(chunk -> !session.lease().includes(chunk)).toList();
        if (missing.isEmpty()) {
            failSession(session, "message.tristorage.linker_offline");
        } else {
            loadChunks(session, missing, () -> scanNetwork(session));
        }
    }

    private static void retainCoreAndOpen(SharedSession session, StorageCoreBlockEntity core) {
        loadChunks(session, Set.of(new ChunkPos(core.getPos())), () -> {
            if (isActive(session) && isValidCore(session, core)) {
                session.setCore(core);
                session.setLoading(false);
                session.touch(session.server().getTicks());
                openWaitingPlayers(session, core);
            } else {
                failSession(session, "message.tristorage.remote_unavailable");
            }
        });
    }

    private static void openWaitingPlayers(SharedSession session, StorageCoreBlockEntity core) {
        PENDING.values().stream().filter(pending -> pending.session() == session).toList()
                .forEach(pending -> openTerminal(pending, core));
    }

    private static void openTerminal(PendingRequest pending, StorageCoreBlockEntity core) {
        ServerPlayerEntity player = eligiblePlayer(pending);
        if (player == null || !isValidCore(pending.session(), core)) {
            cancelPending(pending.playerId());
            return;
        }
        if (!hasDimensionalAccess(player, pending.session())) {
            cancelPending(pending.playerId());
            player.sendMessage(Text.translatable("message.tristorage.remote_requires_antenna"), true);
            return;
        }
        if (!PENDING.remove(pending.playerId(), pending)) {
            return;
        }

        SharedSession session = pending.session();
        RemoteAccessHandle handle = acquire(session, player);
        try {
            OptionalInt opened = player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inventory, ignored) ->
                            new TerminalScreenHandler(syncId, inventory, core, handle),
                    Text.translatable("screen.tristorage.remote_terminal")));
            if (opened.isEmpty()) {
                handle.close();
            }
        } catch (RuntimeException error) {
            handle.close();
            player.sendMessage(Text.translatable("message.tristorage.remote_unavailable"), true);
            LOGGER.error("Could not open remote storage for {}", player.getName().getString(), error);
        }
    }

    private static RemoteAccessHandle acquire(SharedSession session, ServerPlayerEntity player) {
        session.incrementOpenUsers();
        session.touch(session.server().getTicks());
        return new RemoteAccessHandle(
                () -> release(session),
                () -> isValidOpenSession(session, player));
    }

    private static void release(SharedSession session) {
        session.decrementOpenUsers();
        session.touch(session.server().getTicks());
    }

    private static void loadChunks(SharedSession session, Collection<ChunkPos> requested,
                                   Runnable continuation) {
        if (!isActive(session)) {
            return;
        }
        List<ChunkPos> newChunks = requested.stream()
                .filter(chunk -> !session.lease().includes(chunk)).distinct().toList();
        if (!RemoteLoadPolicy.withinChunkBudget(session.lease().chunkCount(), newChunks.size())) {
            failSession(session, "message.tristorage.remote_too_large");
            return;
        }

        List<CompletableFuture<Boolean>> loads = new ArrayList<>(requested.size());
        try {
            for (ChunkPos chunk : requested) {
                loads.add(session.lease().includeAsync(chunk));
            }
        } catch (RuntimeException error) {
            LOGGER.error("Could not schedule a remote TriStorage chunk load", error);
            failSession(session, "message.tristorage.remote_unavailable");
            return;
        }

        session.touch(session.server().getTicks());
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> session.server().execute(() -> {
                    if (!isActive(session)) {
                        return;
                    }
                    boolean loaded = error == null
                            && loads.stream().allMatch(load -> load.getNow(false));
                    if (loaded) {
                        continuation.run();
                    } else {
                        failSession(session, "message.tristorage.remote_unavailable");
                    }
                }));
    }

    private static StorageCoreBlockEntity validateReadyCore(SharedSession session) {
        StorageCoreBlockEntity core = session.core();
        if (core == null) {
            return null;
        }
        if (isValidCore(session, core)
                && StorageNetwork.findCoreLoaded(session.key().world(), session.key().linkerPos()).core() == core) {
            return core;
        }
        invalidateSession(session);
        return null;
    }

    private static boolean isValidCore(SharedSession session, StorageCoreBlockEntity core) {
        return isActive(session)
                && !core.isRemoved()
                && core.getWorld() == session.key().world()
                && session.key().world().getBlockEntity(core.getPos()) == core
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
                    ServerPlayerEntity player = pending.server().getPlayerManager()
                            .getPlayer(pending.playerId());
                    return player == null || !hasDimensionalAccess(player, session, linker);
                }).toList();
        for (PendingRequest pending : rejected) {
            if (PENDING.remove(pending.playerId(), pending)) {
                ServerPlayerEntity player = pending.server().getPlayerManager()
                        .getPlayer(pending.playerId());
                if (player != null) {
                    player.sendMessage(
                            Text.translatable("message.tristorage.remote_requires_antenna"), true);
                }
            }
        }
    }

    private static boolean isValidOpenSession(SharedSession session, ServerPlayerEntity player) {
        return isActive(session) && !player.isDisconnected()
                && session.core() != null && isValidCore(session, session.core())
                && hasDimensionalAccess(player, session);
    }

    private static boolean hasDimensionalAccess(ServerPlayerEntity player, SharedSession session) {
        return session.key().world().getBlockEntity(session.key().linkerPos())
                instanceof LinkerBlockEntity linker
                && hasDimensionalAccess(player, session, linker);
    }

    private static boolean hasDimensionalAccess(ServerPlayerEntity player, SharedSession session,
                                                LinkerBlockEntity linker) {
        return RemoteDimensionPolicy.canAccess(
                player.getEntityWorld().getRegistryKey().equals(World.OVERWORLD),
                session.key().world().getRegistryKey().equals(World.OVERWORLD),
                linker.isAntennaActive());
    }

    private static ServerPlayerEntity eligiblePlayer(PendingRequest pending) {
        if (PENDING.get(pending.playerId()) != pending || !isActive(pending.session())) {
            return null;
        }
        ServerPlayerEntity player = pending.server().getPlayerManager().getPlayer(pending.playerId());
        if (player == null || player.currentScreenHandler != player.playerScreenHandler) {
            return null;
        }
        SessionKey key = pending.session().key();
        return RemoteTabletItem.isLinkedTo(
                player.getStackInHand(pending.hand()),
                key.world().getRegistryKey(), key.linkerPos()) ? player : null;
    }

    private static boolean isActive(SharedSession session) {
        return SESSIONS.get(session.key()) == session && !session.lease().isReleased();
    }

    private static void expireRequestsAndSessions(MinecraftServer server) {
        PENDING.values().stream()
                .filter(pending -> pending.server() == server)
                .filter(pending -> eligiblePlayer(pending) == null).toList()
                .forEach(pending -> cancelPending(pending.playerId()));

        for (PendingRequest pending : PENDING.values().stream()
                .filter(value -> value.server() == server)
                .filter(value -> RemoteLoadPolicy.hasTimedOut(
                        value.startedAtTick(), server.getTicks())).toList()) {
            if (PENDING.remove(pending.playerId(), pending)) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(pending.playerId());
                if (player != null) {
                    player.sendMessage(Text.translatable("message.tristorage.remote_timeout"), true);
                }
            }
        }

        SESSIONS.values().stream()
                .filter(session -> session.server() == server)
                .filter(session -> session.openUsers() == 0)
                .filter(session -> !hasPendingPlayers(session))
                .filter(session -> RemoteLoadPolicy.warmSessionExpired(
                        session.lastUsedTick(), server.getTicks())).toList()
                .forEach(RemoteAccessManager::invalidateSession);
    }

    private static boolean hasPendingPlayers(SharedSession session) {
        return PENDING.values().stream().anyMatch(pending -> pending.session() == session);
    }

    private static void failSession(SharedSession session, String translationKey) {
        if (!SESSIONS.remove(session.key(), session)) {
            return;
        }
        session.lease().release();
        for (PendingRequest pending : PENDING.values().stream()
                .filter(value -> value.session() == session).toList()) {
            if (PENDING.remove(pending.playerId(), pending)) {
                ServerPlayerEntity player = pending.server().getPlayerManager()
                        .getPlayer(pending.playerId());
                if (player != null) {
                    player.sendMessage(Text.translatable(translationKey), true);
                }
            }
        }
    }

    private static void cancelPending(UUID playerId) {
        PendingRequest pending = PENDING.remove(playerId);
        if (pending != null) {
            pending.session().touch(pending.server().getTicks());
        }
    }

    private static void invalidateSession(SharedSession session) {
        if (SESSIONS.remove(session.key(), session)) {
            session.lease().release();
        }
    }

    private static void evictOldestIdleSession(MinecraftServer server) {
        long count = SESSIONS.values().stream().filter(session -> session.server() == server).count();
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
        PENDING.entrySet().removeIf(entry -> entry.getValue().server() == server);
        SESSIONS.values().stream().filter(session -> session.server() == server).toList()
                .forEach(RemoteAccessManager::invalidateSession);
    }

    private record PendingRequest(MinecraftServer server, UUID playerId, Hand hand,
                                  SharedSession session, int startedAtTick) {
        private boolean matches(SessionKey candidateKey, Hand candidateHand) {
            return session.key().equals(candidateKey) && hand == candidateHand;
        }
    }

    private record SessionKey(ServerWorld world, BlockPos linkerPos) {
    }

    private static final class SharedSession {
        private final MinecraftServer server;
        private final SessionKey key;
        private final RemoteChunkLease lease;
        private int lastUsedTick;
        private int openUsers;
        private boolean loading;
        private StorageCoreBlockEntity core;

        private SharedSession(MinecraftServer server, SessionKey key,
                              RemoteChunkLease lease, int lastUsedTick) {
            this.server = server;
            this.key = key;
            this.lease = lease;
            this.lastUsedTick = lastUsedTick;
        }

        private MinecraftServer server() { return server; }
        private SessionKey key() { return key; }
        private RemoteChunkLease lease() { return lease; }
        private int lastUsedTick() { return lastUsedTick; }
        private void touch(int tick) { lastUsedTick = tick; }
        private int openUsers() { return openUsers; }
        private void incrementOpenUsers() { openUsers++; }
        private void decrementOpenUsers() { if (openUsers > 0) openUsers--; }
        private boolean loading() { return loading; }
        private void setLoading(boolean value) { loading = value; }
        private StorageCoreBlockEntity core() { return core; }
        private void setCore(StorageCoreBlockEntity value) { core = value; }
    }
}
