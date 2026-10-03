package com.andresblue.tristorage.version;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Chunk coordinates. ChunkPos became a record in Minecraft 26.1: it lost its
 * BlockPos constructor and its public fields turned into accessors.
 */
public final class Chunks {
    private Chunks() {
    }

    /** The chunk a block is in. */
    public static ChunkPos of(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static int x(ChunkPos chunk) {
        //? if >=26.1 {
        /*return chunk.x();
        *///?} else {
        return chunk.x;
        //?}
    }

    public static int z(ChunkPos chunk) {
        //? if >=26.1 {
        /*return chunk.z();
        *///?} else {
        return chunk.z;
        //?}
    }
}
