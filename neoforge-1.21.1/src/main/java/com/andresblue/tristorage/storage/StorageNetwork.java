package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.block.NetworkBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

public final class StorageNetwork {
    private static final int MAX_VISITED_BLOCKS = 64;

    private StorageNetwork() {
    }

    public static StorageCoreBlockEntity findCore(Level level, BlockPos origin) {
        Queue<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        open.add(origin.immutable());

        while (!open.isEmpty() && visited.size() < MAX_VISITED_BLOCKS) {
            BlockPos current = open.remove();
            if (!visited.add(current)) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(current);
            if (entity instanceof StorageCoreBlockEntity core) {
                return core;
            }
            if (!(level.getBlockState(current).getBlock() instanceof NetworkBlock)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                if (!visited.contains(neighbor)
                        && level.getBlockState(neighbor).getBlock() instanceof NetworkBlock) {
                    open.add(neighbor.immutable());
                }
            }
        }
        return null;
    }

    /**
     * Searches only chunks that are already FULL and records the chunks that
     * would need to be requested to continue. This is the remote-safe version
     * of {@link #findCore}; it cannot trigger a synchronous chunk load through
     * getBlockState/getBlockEntity while walking across a chunk boundary.
     */
    public static LoadedSearch findCoreLoaded(ServerLevel level, BlockPos origin) {
        Queue<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<ChunkPos> missingChunks = new LinkedHashSet<>();
        open.add(origin.immutable());

        while (!open.isEmpty() && visited.size() < MAX_VISITED_BLOCKS) {
            BlockPos current = open.remove();
            if (!visited.add(current)) {
                continue;
            }
            ChunkPos currentChunk = new ChunkPos(current);
            if (!level.getChunkSource().hasChunk(currentChunk.x, currentChunk.z)) {
                missingChunks.add(currentChunk);
                continue;
            }

            BlockEntity entity = level.getBlockEntity(current);
            if (entity instanceof StorageCoreBlockEntity core) {
                return new LoadedSearch(core, Set.of());
            }
            if (!(level.getBlockState(current).getBlock() instanceof NetworkBlock)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);
                if (visited.contains(neighbor)) {
                    continue;
                }
                ChunkPos neighborChunk = new ChunkPos(neighbor);
                if (!level.getChunkSource().hasChunk(neighborChunk.x, neighborChunk.z)) {
                    missingChunks.add(neighborChunk);
                } else if (level.getBlockState(neighbor).getBlock() instanceof NetworkBlock) {
                    open.add(neighbor.immutable());
                }
            }
        }
        return new LoadedSearch(null, Set.copyOf(missingChunks));
    }

    public record LoadedSearch(StorageCoreBlockEntity core, Set<ChunkPos> missingChunks) {
    }
}
