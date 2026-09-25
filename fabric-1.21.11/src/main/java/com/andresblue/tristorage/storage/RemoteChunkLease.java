package com.andresblue.tristorage.storage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * A bounded, non-persistent group of asynchronous chunk-loading tickets.
 * Leases sharing a chunk also share its future and reference count, so one
 * remote user can never unload a chunk still used by another session.
 */
public final class RemoteChunkLease {
    private static final int TICKET_LEVEL = 33;
    public static final ChunkTicketType TICKET_TYPE =
            new ChunkTicketType(ChunkTicketType.NO_EXPIRATION, ChunkTicketType.FOR_LOADING);
    private static final Map<ServerWorld, Map<ChunkPos, SharedLoad>> ACTIVE_LOADS =
            new WeakHashMap<>();

    private final ServerWorld world;
    private final Map<ChunkPos, CompletableFuture<Boolean>> chunkLoads = new HashMap<>();
    private boolean released;

    private RemoteChunkLease(ServerWorld world) {
        this.world = world;
    }

    public static RemoteChunkLease create(ServerWorld world) {
        return new RemoteChunkLease(world);
    }

    public CompletableFuture<Boolean> includeAsync(BlockPos target) {
        return includeAsync(new ChunkPos(target));
    }

    public CompletableFuture<Boolean> includeAsync(ChunkPos chunkPos) {
        if (released) {
            throw new IllegalStateException("Cannot extend a released remote chunk lease");
        }
        CompletableFuture<Boolean> existing = chunkLoads.get(chunkPos);
        if (existing != null) {
            return existing;
        }
        CompletableFuture<Boolean> load = retainChunk(world, chunkPos);
        chunkLoads.put(chunkPos, load);
        return load;
    }

    public boolean includes(ChunkPos chunkPos) {
        return chunkLoads.containsKey(chunkPos);
    }

    public int chunkCount() {
        return chunkLoads.size();
    }

    public MinecraftServer server() {
        return world.getServer();
    }

    public boolean isReleased() {
        return released;
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        for (ChunkPos chunkPos : chunkLoads.keySet()) {
            releaseChunk(world, chunkPos);
        }
        chunkLoads.clear();
    }

    private static synchronized CompletableFuture<Boolean> retainChunk(
            ServerWorld world, ChunkPos chunkPos) {
        Map<ChunkPos, SharedLoad> worldLoads =
                ACTIVE_LOADS.computeIfAbsent(world, ignored -> new HashMap<>());
        SharedLoad current = worldLoads.get(chunkPos);
        if (current != null) {
            current.users++;
            return current.future;
        }

        CompletableFuture<Boolean> future;
        try {
            if (world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                world.getChunkManager().addTicket(TICKET_TYPE, chunkPos, TICKET_LEVEL);
                future = CompletableFuture.completedFuture(true);
            } else {
                future = world.getChunkManager()
                        .addChunkLoadingTicket(TICKET_TYPE, chunkPos, TICKET_LEVEL)
                        .handle((ignored, error) -> error == null);
            }
        } catch (RuntimeException error) {
            world.getChunkManager().removeTicket(TICKET_TYPE, chunkPos, TICKET_LEVEL);
            throw error;
        }
        worldLoads.put(chunkPos, new SharedLoad(future));
        return future;
    }

    private static synchronized void releaseChunk(ServerWorld world, ChunkPos chunkPos) {
        Map<ChunkPos, SharedLoad> worldLoads = ACTIVE_LOADS.get(world);
        if (worldLoads == null) {
            return;
        }
        SharedLoad current = worldLoads.get(chunkPos);
        if (current == null) {
            return;
        }
        if (--current.users <= 0) {
            worldLoads.remove(chunkPos);
            world.getChunkManager().removeTicket(TICKET_TYPE, chunkPos, TICKET_LEVEL);
        }
        if (worldLoads.isEmpty()) {
            ACTIVE_LOADS.remove(world);
        }
    }

    private static final class SharedLoad {
        private final CompletableFuture<Boolean> future;
        private int users = 1;

        private SharedLoad(CompletableFuture<Boolean> future) {
            this.future = future;
        }
    }
}
