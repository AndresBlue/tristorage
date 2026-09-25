package com.andresblue.tristorage.storage;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Keeps the chunks of one shared remote-storage session loaded. Tickets are
 * temporary and non-persistent; concurrent screens reference the owning
 * session instead of creating duplicate chunk requests.
 */
public final class RemoteChunkLease {
    private static final int TICKET_LEVEL = 33;
    private static final ChunkTicketType<UUID> TICKET_TYPE =
            ChunkTicketType.create("tristorage_remote", UUID::compareTo);

    private final ServerWorld world;
    private final UUID ticketId;
    private final Map<ChunkPos, CompletableFuture<Boolean>> chunkLoads = new HashMap<>();
    private boolean released;

    private RemoteChunkLease(ServerWorld world, UUID ticketId) {
        this.world = world;
        this.ticketId = ticketId;
    }

    public static RemoteChunkLease create(ServerWorld world) {
        return new RemoteChunkLease(world, UUID.randomUUID());
    }

    /**
     * Adds a non-persistent FULL ticket and returns immediately with the
     * chunk's future. Most importantly, this method never calls
     * ServerWorld#getChunk, which waits synchronously on the server thread.
     */
    public CompletableFuture<Boolean> includeAsync(BlockPos target) {
        return includeAsync(new ChunkPos(target));
    }

    public CompletableFuture<Boolean> includeAsync(ChunkPos chunkPos) {
        if (released) {
            throw new IllegalStateException("Cannot extend a released remote chunk lease");
        }
        CompletableFuture<Boolean> existing = chunkLoads.get(chunkPos);
        if (existing != null) {
            StorageMetrics.increment("remote.chunk_lease_hits");
            return existing;
        }
        long loadStarted = StorageMetrics.startTimer();
        world.getChunkManager().addTicket(TICKET_TYPE, chunkPos, TICKET_LEVEL, ticketId);
        try {
            CompletableFuture<Boolean> load;
            if (world.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                load = CompletableFuture.completedFuture(true);
                StorageMetrics.increment("remote.chunks_already_loaded");
            } else {
                load = world.getChunkManager()
                        .getChunkFutureSyncOnMainThread(
                                chunkPos.x, chunkPos.z, ChunkStatus.FULL, true)
                        .thenApply(result -> result.left().isPresent());
            }
            load.whenComplete((loaded, error) -> {
                StorageMetrics.stopTimer("remote.chunk_load", loadStarted);
                StorageMetrics.increment(error == null && Boolean.TRUE.equals(loaded)
                        ? "remote.chunk_load_success" : "remote.chunk_load_failure");
            });
            chunkLoads.put(chunkPos, load);
            return load;
        } catch (RuntimeException exception) {
            world.getChunkManager().removeTicket(
                    TICKET_TYPE, chunkPos, TICKET_LEVEL, ticketId);
            throw exception;
        }
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
        StorageMetrics.add("remote.chunk_tickets_released", chunkLoads.size());
        for (ChunkPos chunkPos : chunkLoads.keySet()) {
            world.getChunkManager().removeTicket(
                    TICKET_TYPE, chunkPos, TICKET_LEVEL, ticketId);
        }
        chunkLoads.clear();
    }
}
