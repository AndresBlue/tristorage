package com.andresblue.tristorage.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageRepositoryJournalTest {
    @TempDir
    Path temporary;

    @Test
    void framedJournalRoundTripsAndTruncatesAnIncompleteTail() throws Exception {
        Path journal = temporary.resolve("storage.journal");
        CompoundTag frame = new CompoundTag();
        frame.putLong("Revision", 42);
        invokeAppend(journal, frame);
        long validSize = Files.size(journal);

        Files.write(journal, new byte[]{0x54, 0x53, 0x4A},
                StandardOpenOption.APPEND);
        List<CompoundTag> recovered = invokeRead(journal);

        assertEquals(1, recovered.size());
        assertEquals(42, recovered.get(0).getLong("Revision"));
        assertEquals(validSize, Files.size(journal));
    }

    @Test
    void crcFailureNeverPublishesTheDamagedFrame() throws Exception {
        Path journal = temporary.resolve("crc.journal");
        CompoundTag frame = new CompoundTag();
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
        CompoundTag snapshot = snapshot(10, 2, entry(1, 5));
        List<CompoundTag> frames = List.of(
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
        List<CompoundTag> frames = List.of(
                frame(1, 1, entry(1, 4)),
                frame(2, 1, removal(1)),
                frame(3, 1, entry(2, 9)));

        StorageRepository.Prepared prepared = StorageRepository.replay(null, frames);

        assertEquals(3, prepared.revision());
        assertEquals(1, prepared.entries().size());
        assertEquals(9, countOf(prepared, 2));
    }

    private static CompoundTag snapshot(long revision, int chests, CompoundTag... entries) {
        CompoundTag root = new CompoundTag();
        root.putLong("Revision", revision);
        root.putInt("InstalledChests", chests);
        ListTag list = new ListTag();
        list.addAll(List.of(entries));
        root.put("Entries", list);
        return root;
    }

    private static CompoundTag frame(long revision, int chests, CompoundTag... operations) {
        CompoundTag frame = new CompoundTag();
        frame.putLong("Revision", revision);
        frame.putInt("InstalledChests", chests);
        ListTag list = new ListTag();
        list.addAll(List.of(operations));
        frame.put("Operations", list);
        return frame;
    }

    private static CompoundTag entry(long entryId, long count) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("EntryId", entryId);
        entry.putLong("Count", count);
        return entry;
    }

    private static CompoundTag removal(long entryId) {
        CompoundTag removal = new CompoundTag();
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

    private static void invokeAppend(Path path, CompoundTag frame) throws Exception {
        Method method = StorageRepository.class.getDeclaredMethod(
                "appendFrame", Path.class, CompoundTag.class, boolean.class);
        method.setAccessible(true);
        method.invoke(null, path, frame, false);
    }

    @SuppressWarnings("unchecked")
    private static List<CompoundTag> invokeRead(Path path) throws Exception {
        Method method = StorageRepository.class.getDeclaredMethod("readFrames", Path.class);
        method.setAccessible(true);
        return (List<CompoundTag>) method.invoke(null, path);
    }
}
