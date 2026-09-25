package com.andresblue.tristorage.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void everyBlockUsesTheMinecraft121PickaxeTagPath() throws Exception {
        String path = "data/minecraft/tags/block/mineable/pickaxe.json";
        Enumeration<URL> resources = getClass().getClassLoader().getResources(path);
        Set<String> tagged = new HashSet<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (var reader = new InputStreamReader(
                    resource.openStream(), StandardCharsets.UTF_8)) {
                JsonArray values = JsonParser.parseReader(reader)
                        .getAsJsonObject().getAsJsonArray("values");
                for (var value : values) {
                    if (value.isJsonPrimitive()) {
                        tagged.add(value.getAsString());
                    } else if (value.isJsonObject()) {
                        JsonObject object = value.getAsJsonObject();
                        if (object.has("id")) {
                            tagged.add(object.get("id").getAsString());
                        }
                    }
                }
            }
        }
        for (String block : BLOCKS) {
            assertTrue(tagged.contains("tristorage:" + block),
                    () -> block + " must be mineable with a pickaxe");
        }
    }

    @Test
    void everyMineableBlockHasALootTable() {
        for (String block : BLOCKS) {
            String path = "data/tristorage/loot_table/blocks/" + block + ".json";
            assertNotNull(getClass().getClassLoader().getResource(path),
                    () -> "Missing loot table for " + block);
        }
    }
}
