package com.andresblue.tristorage.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.ChunkAccess;

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
    private static final TicketType<UUID> TICKET_TYPE =
            TicketType.create("tristorage_remote", UUID::compareTo);

    private final ServerLevel level;
    private final UUID ticketId;
    private final Map<ChunkPos, CompletableFuture<Boolean>> chunkLoads = new HashMap<>();
    private boolean released;

    private RemoteChunkLease(ServerLevel level, UUID ticketId) {
        this.level = level;
        this.ticketId = ticketId;
    }

    public static RemoteChunkLease create(ServerLevel level) {
        return new RemoteChunkLease(level, UUID.randomUUID());
    }

    /**
     * Adds a non-persistent FULL ticket and returns immediately with the
     * chunk's future. Most importantly, this method never calls
     * ServerLevel#getChunk, which waits synchronously on the server thread.
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
            return existing;
        }
        level.getChunkSource().addRegionTicket(TICKET_TYPE, chunkPos, TICKET_LEVEL, ticketId);
        try {
            CompletableFuture<Boolean> load;
            if (level.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
                load = CompletableFuture.completedFuture(true);
            } else {
                load = level.getChunkSource()
                        .getChunkFuture(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true)
                        .thenApply(result -> result.orElse(null) instanceof ChunkAccess);
            }
            chunkLoads.put(chunkPos, load);
            return load;
        } catch (RuntimeException exception) {
            level.getChunkSource().removeRegionTicket(
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
        return level.getServer();
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
            level.getChunkSource().removeRegionTicket(
                    TICKET_TYPE, chunkPos, TICKET_LEVEL, ticketId);
        }
        chunkLoads.clear();
    }
}
