package com.andresblue.tristorage.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** A broken Core itself keeps its storage: the loot table carries the data along. */
class CoreLootTableTest {
    @Test
    void coreDropCarriesItsBlockEntityData() throws Exception {
        //? if >=1.21 {
        /*String path = "/data/tristorage/loot_table/blocks/iron_storage_core.json";
        *///?} else {
        String path = "/data/tristorage/loot_tables/blocks/iron_storage_core.json";
        //?}
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "missing core loot table");
            JsonObject table = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject function = table.getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject()
                    .getAsJsonArray("functions").get(0).getAsJsonObject();

            assertEquals("block_entity", function.get("source").getAsString());
            //? if >=1.20.5 {
            /*assertEquals("minecraft:copy_components", function.get("function").getAsString());
            assertEquals("minecraft:block_entity_data",
                    function.getAsJsonArray("include").get(0).getAsString());
            *///?} else {
            assertEquals("minecraft:copy_nbt", function.get("function").getAsString());
            assertEquals(9, function.getAsJsonArray("ops").size());
            //?}
        }
    }
}
