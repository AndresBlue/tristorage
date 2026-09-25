package com.andresblue.tristorage.storage;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
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

    @Test
    void replaySkipsFramesTheSnapshotAlreadyContains() {
        // A sealed journal left by an interrupted compaction still holds
        // revision 8, while a later checkpoint wrote the revision-10 snapshot.
        NbtCompound snapshot = snapshot(10, 2, entry(1, 5));
        List<NbtCompound> frames = List.of(
                frame(8, 1, entry(1, 100)),
                frame(9, 1, removal(1)),
                frame(11, 2, entry(1, 7), entry(2, 3)));

        StorageRepository.Prepared prepared = StorageRepository.replay(snapshot, frames);

        assertEquals(11, prepared.revision());
        assertEquals(2, prepared.chests());
        assertEquals(2, prepared.entries().size());
        assertEquals(7, countOf(prepared, 1));
        assertEquals(3, countOf(prepared, 2));
    }

    @Test
    void replayWithoutSnapshotAppliesEveryFrame() {
        List<NbtCompound> frames = List.of(
                frame(1, 1, entry(1, 4)),
                frame(2, 1, removal(1)),
                frame(3, 1, entry(2, 9)));

        StorageRepository.Prepared prepared = StorageRepository.replay(null, frames);

        assertEquals(3, prepared.revision());
        assertEquals(1, prepared.entries().size());
        assertEquals(9, countOf(prepared, 2));
    }

    private static NbtCompound snapshot(long revision, int chests, NbtCompound... entries) {
        NbtCompound root = new NbtCompound();
        root.putLong("Revision", revision);
        root.putInt("InstalledChests", chests);
        NbtList list = new NbtList();
        list.addAll(List.of(entries));
        root.put("Entries", list);
        return root;
    }

    private static NbtCompound frame(long revision, int chests, NbtCompound... operations) {
        NbtCompound frame = new NbtCompound();
        frame.putLong("Revision", revision);
        frame.putInt("InstalledChests", chests);
        NbtList list = new NbtList();
        list.addAll(List.of(operations));
        frame.put("Operations", list);
        return frame;
    }

    private static NbtCompound entry(long entryId, long count) {
        NbtCompound entry = new NbtCompound();
        entry.putLong("EntryId", entryId);
        entry.putLong("Count", count);
        return entry;
    }

    private static NbtCompound removal(long entryId) {
        NbtCompound removal = new NbtCompound();
        removal.putLong("EntryId", entryId);
        removal.putBoolean("Removed", true);
        return removal;
    }

    private static long countOf(StorageRepository.Prepared prepared, long entryId) {
        return prepared.entries().stream()
                .filter(entry -> entry.getLong("EntryId") == entryId)
                .findFirst()
                .orElseThrow()
                .getLong("Count");
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
