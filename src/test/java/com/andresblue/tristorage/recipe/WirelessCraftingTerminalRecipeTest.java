package com.andresblue.tristorage.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WirelessCraftingTerminalRecipeTest {
    @Test
    void recipeIsTheExactPostNetherUpgrade() throws Exception {
        // The data pack is written in the 1.20.1 format and upgraded by the build.
        //? if >=1.21 {
        /*String path = "/data/tristorage/recipe/wireless_crafting_terminal.json";
        String resultKey = "id";
        *///?} else {
        String path = "/data/tristorage/recipes/wireless_crafting_terminal.json";
        String resultKey = "item";
        //?}
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, "missing wireless crafting recipe resource");
            JsonObject recipe = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();

            assertEquals("tristorage:wireless_crafting_terminal_upgrade",
                    recipe.get("type").getAsString());
            assertEquals("ENE", recipe.getAsJsonArray("pattern").get(0).getAsString());
            assertEquals("BCB", recipe.getAsJsonArray("pattern").get(1).getAsString());
            assertEquals("QTQ", recipe.getAsJsonArray("pattern").get(2).getAsString());

            JsonObject keys = recipe.getAsJsonObject("key");
            assertEquals("minecraft:ender_pearl",
                    itemOf(keys.get("E")));
            assertEquals("minecraft:netherite_ingot",
                    itemOf(keys.get("N")));
            assertEquals("minecraft:blaze_rod",
                    itemOf(keys.get("B")));
            assertEquals("minecraft:crafting_table",
                    itemOf(keys.get("C")));
            assertEquals("minecraft:quartz",
                    itemOf(keys.get("Q")));
            assertEquals("tristorage:remote_tablet",
                    itemOf(keys.get("T")));
            assertEquals("tristorage:wireless_crafting_terminal",
                    recipe.getAsJsonObject("result").get(resultKey).getAsString());
        }
    }

    /** An ingredient's item: an object before 1.21.2, a plain id from then on. */
    private static String itemOf(JsonElement ingredient) {
        return ingredient.isJsonPrimitive() ? ingredient.getAsString()
                : ingredient.getAsJsonObject().get("item").getAsString();
    }
}
