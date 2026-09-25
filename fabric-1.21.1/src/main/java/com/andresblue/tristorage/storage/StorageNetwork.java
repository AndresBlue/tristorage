package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.block.NetworkBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashSet;
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
}
