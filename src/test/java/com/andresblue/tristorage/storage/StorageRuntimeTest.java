package com.andresblue.tristorage.storage;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageRuntimeTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void metrics() {
        StorageMetrics.setEnabled(true);
        StorageMetrics.reset();
    }

    @AfterEach
    void cleanup() {
        StorageMetrics.setEnabled(false);
        StorageTickCoordinator.flush();
    }

    @Test
    void oneHundredMutationsBecomeOneRevision() {
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        for (int index = 0; index < 100; index++) {
            assertEquals(1, runtime.insert(new ItemStack(Items.STONE),
                    1, 1_000, 1_000_000));
        }
        assertEquals(0, runtime.revision());

        StorageTickCoordinator.flush();

        assertEquals(1, runtime.revision());
        assertEquals(1, StorageMetrics.snapshot().get("mutation_batches"));
        assertEquals(0, metric("full_sorts"));
        assertEquals(0, metric("full_scans"));
    }

    @Test
    void staleEntryIdNeverTargetsAReplacement() {
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        runtime.insert(new ItemStack(Items.STONE), 1, 100, 100);
        long oldId = runtime.browse(0, 54, StorageRuntime.EntryOrder.REGISTRY,
                TerminalFilter.sanitize("", TerminalFilter.CategoryMode.NONE,
                        TerminalFilter.ALL)).entries().get(0).id();
        runtime.extract(oldId, 64);
        runtime.insert(new ItemStack(Items.DIRT), 1, 100, 100);

        assertTrue(runtime.extract(oldId, 64).isEmpty());
        long replacement = runtime.browse(0, 54, StorageRuntime.EntryOrder.REGISTRY,
                TerminalFilter.sanitize("", TerminalFilter.CategoryMode.NONE,
                        TerminalFilter.ALL)).entries().get(0).id();
        assertTrue(replacement > oldId);
    }

    @Test
    void cachedFilteredViewIsSharedAndUpdatedIncrementally() {
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        runtime.insert(new ItemStack(Items.STONE), 2, 100, 100);
        TerminalFilter.Selection filter = TerminalFilter.sanitize(
                "stone", TerminalFilter.CategoryMode.NONE, TerminalFilter.ALL);

        StorageRuntime.BrowseResult first = runtime.browse(
                0, 54, StorageRuntime.EntryOrder.COUNT, filter);
        StorageRuntime.BrowseResult second = runtime.browse(
                0, 54, StorageRuntime.EntryOrder.COUNT, filter);
        assertEquals(1, first.filteredTypes());
        assertEquals(2, second.filteredItems());
        assertEquals(1, metric("view_builds"));

        runtime.insert(new ItemStack(Items.STONE), 3, 100, 100);
        StorageRuntime.BrowseResult updated = runtime.browse(
                0, 54, StorageRuntime.EntryOrder.COUNT, filter);
        assertEquals(5, updated.filteredItems());
        assertEquals(1, metric("view_builds"));
        assertFalse(updated.entries().isEmpty());
    }

    @Test
    void recipeCandidatesUseThePerItemIndexInsteadOfTheVisiblePage() {
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        runtime.insert(new ItemStack(Items.STONE), 50, 100, 1_000);
        runtime.insert(new ItemStack(Items.DRAGON_HEAD), 2, 100, 1_000);

        var matches = runtime.matchingEntries(Ingredient.of(Items.DRAGON_HEAD));

        assertEquals(1, matches.size());
        assertTrue(matches.get(0).stack().is(Items.DRAGON_HEAD));
        assertEquals(2, matches.get(0).count());
        assertEquals(0, metric("recipe_transfer_fallback_scans"));
    }

    @Test
    void exactVariantExtractionSupportsCraftingGridRefill() {
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        ItemStack named = new ItemStack(Items.OAK_PLANKS);
        named.getOrCreateTag().putString("TriStorageTest", "named");
        ItemStack ordinary = new ItemStack(Items.OAK_PLANKS);
        runtime.insert(named, 4, 100, 1_000);
        runtime.insert(ordinary, 4, 100, 1_000);

        ItemStack extracted = runtime.extractMatching(named, 1);

        assertEquals(1, extracted.getCount());
        assertEquals("named", extracted.getTag().getString("TriStorageTest"));
        assertEquals(7, runtime.totalItems());
    }

    @Test
    void categoryViewsUseTheIncrementalMembershipIndex() {
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        runtime.insert(new ItemStack(Items.STONE), 64, 100, 1_000);
        TerminalFilter.Selection filter = TerminalFilter.sanitize(
                "", TerminalFilter.CategoryMode.MOD, "minecraft");
        runtime.browse(0, 54, StorageRuntime.EntryOrder.REGISTRY, filter);
        runtime.insert(new ItemStack(Items.DIRT), 16, 100, 1_000);
        StorageRuntime.BrowseResult updated = runtime.browse(
                0, 54, StorageRuntime.EntryOrder.REGISTRY, filter);

        assertEquals(1, metric("category_index_scans"));
        assertEquals(0, metric("full_scans"));
        assertEquals(1, metric("view_builds"));
        assertEquals(2, updated.filteredTypes());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fullRecipeFallbackRepairsAMissingIncrementalIndex() throws Exception {
        StorageRuntime runtime = new StorageRuntime(StorageId.random());
        runtime.insert(new ItemStack(Items.SLIME_BALL), 64, 100, 1_000);
        var field = StorageRuntime.class.getDeclaredField("byItem");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(runtime)).clear();

        Ingredient slime = Ingredient.of(Items.SLIME_BALL);
        assertTrue(runtime.matchingEntries(slime).isEmpty());
        assertEquals(1, runtime.matchingAnyEntries(List.of(slime), 9).size());
        assertEquals(1, runtime.matchingEntries(slime).size());
    }

    private static long metric(String key) {
        Map<String, Long> values = StorageMetrics.snapshot();
        return values.getOrDefault(key, 0L);
    }
}
