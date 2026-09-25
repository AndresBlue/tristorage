package com.andresblue.tristorage.version;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
//? if >=1.20.3 {
/*import net.minecraft.nbt.NbtAccounter;
*///?}

import java.io.IOException;
import java.nio.file.Path;

/** Compressed NBT file access; the File overloads became Path overloads in 1.20.3. */
public final class NbtFiles {
    private NbtFiles() {
    }

    public static CompoundTag readCompressed(Path path) throws IOException {
        //? if >=1.20.3 {
        /*return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        *///?} else {
        return NbtIo.readCompressed(path.toFile());
        //?}
    }

    public static void writeCompressed(CompoundTag tag, Path path) throws IOException {
        //? if >=1.20.3 {
        /*NbtIo.writeCompressed(tag, path);
        *///?} else {
        NbtIo.writeCompressed(tag, path.toFile());
        //?}
    }
}
