package com.andresblue.tristorage.storage;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageRepositoryJournalTest {
    @TempDir
    Path temporary;

    @Test
    void framedJournalRoundTripsAndTruncatesAnIncompleteTail() throws Exception {
        Path journal = temporary.resolve("storage.journal");
        NbtCompound frame = new NbtCompound();
        frame.putLong("Revision", 42);
        invokeAppend(journal, frame);
        long validSize = Files.size(journal);

        Files.write(journal, new byte[]{0x54, 0x53, 0x4A},
                StandardOpenOption.APPEND);
        List<NbtCompound> recovered = invokeRead(journal);

        assertEquals(1, recovered.size());
        assertEquals(42, recovered.get(0).getLong("Revision"));
        assertEquals(validSize, Files.size(journal));
    }

    @Test
    void crcFailureNeverPublishesTheDamagedFrame() throws Exception {
        Path journal = temporary.resolve("crc.journal");
        NbtCompound frame = new NbtCompound();
        frame.putString("Payload", "safe");
        invokeAppend(journal, frame);
        byte[] bytes = Files.readAllBytes(journal);
        bytes[bytes.length - 1] ^= 0x7F;
        Files.write(journal, bytes);

        assertTrue(invokeRead(journal).isEmpty());
        assertEquals(0, Files.size(journal));
    }

    private static void invokeAppend(Path path, NbtCompound frame) throws Exception {
        Method method = StorageRepository.class.getDeclaredMethod(
                "appendFrame", Path.class, NbtCompound.class, boolean.class);
        method.setAccessible(true);
        method.invoke(null, path, frame, false);
    }

    @SuppressWarnings("unchecked")
    private static List<NbtCompound> invokeRead(Path path) throws Exception {
        Method method = StorageRepository.class.getDeclaredMethod("readFrames", Path.class);
        method.setAccessible(true);
        return (List<NbtCompound>) method.invoke(null, path);
    }
}
