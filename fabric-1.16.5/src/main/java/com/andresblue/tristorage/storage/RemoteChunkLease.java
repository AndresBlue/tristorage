package com.andresblue.tristorage.storage;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps a remote Linker's chunk loaded only for the lifetime of one open
 * terminal. Tickets are not persistent and each screen owns an independent
 * argument, so concurrent users cannot release one another's chunks.
 */
public final class RemoteChunkLease {
    private static final int TICKET_LEVEL = 33;
    private static final ChunkTicketType<UUID> TICKET_TYPE =
            ChunkTicketType.create("tristorage_remote", UUID::compareTo);

    private final ServerWorld world;
    private final UUID ticketId;
    private final Set<ChunkPos> chunkPositions = new HashSet<>();
    private boolean released;

    private RemoteChunkLease(ServerWorld world, UUID ticketId) {
        this.world = world;
        this.ticketId = ticketId;
    }

    public static RemoteChunkLease acquire(ServerWorld world, BlockPos target) {
        RemoteChunkLease lease = new RemoteChunkLease(world, UUID.randomUUID());
        lease.include(target);
        return lease;
    }

    public void include(BlockPos target) {
        if (released) {
            throw new IllegalStateException("Cannot extend a released remote chunk lease");
        }
        ChunkPos chunkPos = new ChunkPos(target);
        if (!chunkPositions.add(chunkPos)) {
            return;
        }
        world.getChunkManager().addTicket(TICKET_TYPE, chunkPos, TICKET_LEVEL, ticketId);

        // Materialize the FULL chunk immediately; level 33 keeps only this
        // chunk accessible without enabling block or entity ticking.
        world.getChunk(chunkPos.x, chunkPos.z);
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        for (ChunkPos chunkPos : chunkPositions) {
            world.getChunkManager().removeTicket(
                    TICKET_TYPE, chunkPos, TICKET_LEVEL, ticketId);
        }
        chunkPositions.clear();
    }
}
