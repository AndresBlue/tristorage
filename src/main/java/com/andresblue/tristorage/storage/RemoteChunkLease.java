package com.andresblue.tristorage.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
//? if >=1.20.5 {
/*import net.minecraft.world.level.chunk.status.ChunkStatus;
*///?} else {
import net.minecraft.world.level.chunk.ChunkStatus;
//?}
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
    /**
     * Keeps the anchor chunk and its neighbours loaded, and block-ticks the
     * anchor. Before 2.0 the radius was passed as 33, which loaded 33 chunks
     * around every Linker and Core in a remote session.
     */
    private static final int TICKET_RADIUS = 1;
    //? if >=1.21.5 {
    /*// Registered by TriStorageMod.registerTicketTypes. It keeps a dimension
    // active so a remote session into it keeps working without players there.
    public static final TicketType TICKET_TYPE = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    *///?} else {
    private static final TicketType<UUID> TICKET_TYPE =
            TicketType.create("tristorage_remote", UUID::compareTo);
    //?}

    private final ServerLevel world;
    private final UUID ticketId;
    private final Map<ChunkPos, CompletableFuture<Boolean>> chunkLoads = new HashMap<>();
    private boolean released;

    private RemoteChunkLease(ServerLevel world, UUID ticketId) {
        this.world = world;
        this.ticketId = ticketId;
    }

    public static RemoteChunkLease create(ServerLevel world) {
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
        addTicket(chunkPos);
        try {
            CompletableFuture<Boolean> load;
            if (world.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
                load = CompletableFuture.completedFuture(true);
                StorageMetrics.increment("remote.chunks_already_loaded");
            } else {
                load = world.getChunkSource()
                        .getChunkFuture(
                                chunkPos.x, chunkPos.z, ChunkStatus.FULL, true)
                        //? if >=1.20.5 {
                        /*.thenApply(result -> result.isSuccess());
                        *///?} else {
                        .thenApply(result -> result.left().isPresent());
                        //?}
            }
            load.whenComplete((loaded, error) -> {
                StorageMetrics.stopTimer("remote.chunk_load", loadStarted);
                StorageMetrics.increment(error == null && Boolean.TRUE.equals(loaded)
                        ? "remote.chunk_load_success" : "remote.chunk_load_failure");
            });
            chunkLoads.put(chunkPos, load);
            return load;
        } catch (RuntimeException exception) {
            removeTicket(chunkPos);
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
            removeTicket(chunkPos);
        }
        chunkLoads.clear();
    }

    private void addTicket(ChunkPos chunkPos) {
        //? if >=1.21.5 {
        /*world.getChunkSource().addTicketWithRadius(TICKET_TYPE, chunkPos, TICKET_RADIUS);
        *///?} else {
        world.getChunkSource().addRegionTicket(TICKET_TYPE, chunkPos, TICKET_RADIUS, ticketId);
        //?}
    }

    private void removeTicket(ChunkPos chunkPos) {
        //? if >=1.21.5 {
        /*world.getChunkSource().removeTicketWithRadius(TICKET_TYPE, chunkPos, TICKET_RADIUS);
        *///?} else {
        world.getChunkSource().removeRegionTicket(TICKET_TYPE, chunkPos, TICKET_RADIUS, ticketId);
        //?}
    }
}
