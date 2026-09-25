package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.block.NetworkBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

public final class StorageNetwork {
    private static final int MAX_VISITED_BLOCKS = 64;

    private StorageNetwork() {
    }

    public static StorageCoreBlockEntity findCore(World world, BlockPos origin) {
        Queue<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        open.add(origin.toImmutable());

        while (!open.isEmpty() && visited.size() < MAX_VISITED_BLOCKS) {
            BlockPos current = open.remove();
            if (!visited.add(current)) {
                continue;
            }
            BlockEntity entity = world.getBlockEntity(current);
            if (entity instanceof StorageCoreBlockEntity core) {
                return core;
            }
            if (!(world.getBlockState(current).getBlock() instanceof NetworkBlock)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.offset(direction);
                if (!visited.contains(neighbor)
                        && world.getBlockState(neighbor).getBlock() instanceof NetworkBlock) {
                    open.add(neighbor.toImmutable());
                }
            }
        }
        return null;
    }

    /**
     * Traverses only chunks that are already fully loaded. Any boundary chunks
     * needed to continue the search are returned to the asynchronous loader.
     */
    public static LoadedSearch findCoreLoaded(ServerWorld world, BlockPos origin) {
        Queue<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<ChunkPos> missingChunks = new LinkedHashSet<>();
        open.add(origin.toImmutable());

        while (!open.isEmpty() && visited.size() < MAX_VISITED_BLOCKS) {
            BlockPos current = open.remove();
            if (!visited.add(current)) {
                continue;
            }
            ChunkPos currentChunk = new ChunkPos(current);
            if (!world.getChunkManager().isChunkLoaded(currentChunk.x, currentChunk.z)) {
                missingChunks.add(currentChunk);
                continue;
            }
            BlockEntity entity = world.getBlockEntity(current);
            if (entity instanceof StorageCoreBlockEntity core) {
                return new LoadedSearch(core, Set.of());
            }
            if (!(world.getBlockState(current).getBlock() instanceof NetworkBlock)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.offset(direction);
                if (visited.contains(neighbor)) {
                    continue;
                }
                ChunkPos neighborChunk = new ChunkPos(neighbor);
                if (!world.getChunkManager().isChunkLoaded(neighborChunk.x, neighborChunk.z)) {
                    missingChunks.add(neighborChunk);
                } else if (world.getBlockState(neighbor).getBlock() instanceof NetworkBlock) {
                    open.add(neighbor.toImmutable());
                }
            }
        }
        return new LoadedSearch(null, Set.copyOf(missingChunks));
    }

    public record LoadedSearch(StorageCoreBlockEntity core, Set<ChunkPos> missingChunks) {
    }
}
