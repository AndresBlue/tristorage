package com.andresblue.tristorage.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every block can be mined and drops itself, in the data pack as built for
 * this Minecraft version. From 1.21 the game reads tags and loot tables from
 * singular folders and ignores the older plural ones, which once left the
 * 1.21 ports with blocks no pickaxe could speed up.
 */
class BlockMiningResourcesTest {
    private static final Set<String> BLOCKS = Set.of(
            "iron_storage_core",
            "diamond_storage_core",
            "blaze_storage_core",
            "cosmic_storage_core",
            "storage_terminal",
            "crafting_terminal",
            "storage_linker"
    );
    //? if >=1.21 {
    /*private static final String PICKAXE_TAG = "/data/minecraft/tags/block/mineable/pickaxe.json";
    private static final String LOOT_TABLES = "/data/tristorage/loot_table/blocks/";
    *///?} else {
    private static final String PICKAXE_TAG = "/data/minecraft/tags/blocks/mineable/pickaxe.json";
    private static final String LOOT_TABLES = "/data/tristorage/loot_tables/blocks/";
    //?}

    @Test
    void everyBlockIsMineableWithAPickaxe() throws Exception {
        Set<String> tagged = new HashSet<>();
        try (InputStream stream = getClass().getResourceAsStream(PICKAXE_TAG)) {
            assertNotNull(stream, "missing pickaxe tag at " + PICKAXE_TAG);
            for (JsonElement value : JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values")) {
                tagged.add(value.getAsString());
            }
        }
        for (String block : BLOCKS) {
            assertTrue(tagged.contains("tristorage:" + block),
                    () -> block + " must be mineable with a pickaxe");
        }
    }

    @Test
    void everyBlockHasALootTable() {
        for (String block : BLOCKS) {
            assertNotNull(getClass().getResource(LOOT_TABLES + block + ".json"),
                    () -> "missing loot table for " + block);
        }
    }
}
