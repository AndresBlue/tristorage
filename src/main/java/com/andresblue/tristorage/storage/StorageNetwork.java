package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.block.NetworkBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Map;

public final class StorageNetwork {
    private static final int MAX_VISITED_BLOCKS = 64;
    private static final Map<Level, Long> TOPOLOGY_VERSIONS = new WeakHashMap<>();

    private StorageNetwork() {
    }

    public static void markTopologyChanged(Level world) {
        if (!world.isClientSide) {
            TOPOLOGY_VERSIONS.merge(world, 1L, Long::sum);
        }
    }

    public static long topologyVersion(Level world) {
        return TOPOLOGY_VERSIONS.getOrDefault(world, 0L);
    }

    public static StorageCoreBlockEntity findCore(Level world, BlockPos origin) {
        Queue<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        open.add(origin.immutable());

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
                BlockPos neighbor = current.relative(direction);
                if (!visited.contains(neighbor)
                        && world.getBlockState(neighbor).getBlock() instanceof NetworkBlock) {
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
    public static LoadedSearch findCoreLoaded(ServerLevel world, BlockPos origin) {
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
            if (!world.getChunkSource().hasChunk(currentChunk.x, currentChunk.z)) {
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
                BlockPos neighbor = current.relative(direction);
                if (visited.contains(neighbor)) {
                    continue;
                }
                ChunkPos neighborChunk = new ChunkPos(neighbor);
                if (!world.getChunkSource().hasChunk(neighborChunk.x, neighborChunk.z)) {
                    missingChunks.add(neighborChunk);
                } else if (world.getBlockState(neighbor).getBlock() instanceof NetworkBlock) {
                    open.add(neighbor.immutable());
                }
            }
        }
        return new LoadedSearch(null, Set.copyOf(missingChunks));
    }

    public record LoadedSearch(StorageCoreBlockEntity core, Set<ChunkPos> missingChunks) {
    }
}
