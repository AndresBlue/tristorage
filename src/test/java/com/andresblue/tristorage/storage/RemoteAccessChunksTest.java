package com.andresblue.tristorage.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteAccessChunksTest {
    @Test
    void linkerNextToItsCoreNeedsOneChunk() {
        // The layout that broke every tablet open in 1.12.1: both in chunk [-1, -1].
        List<ChunkPos> chunks = RemoteAccessManager.anchorChunks(
                new BlockPos(-5, 64, -5), new BlockPos(-4, 64, -5));

        assertEquals(List.of(new ChunkPos(-1, -1)), chunks);
    }

    @Test
    void linkerAndCoreInDifferentChunksKeepBoth() {
        List<ChunkPos> chunks = RemoteAccessManager.anchorChunks(
                new BlockPos(15, 64, 0), new BlockPos(16, 64, 0));

        assertEquals(List.of(new ChunkPos(0, 0), new ChunkPos(1, 0)), chunks);
    }
}
