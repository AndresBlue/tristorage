package com.andresblue.tristorage.recipe;

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
        String path = "/data/tristorage/recipes/wireless_crafting_terminal.json";
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
                    keys.getAsJsonObject("E").get("item").getAsString());
            assertEquals("minecraft:netherite_ingot",
                    keys.getAsJsonObject("N").get("item").getAsString());
            assertEquals("minecraft:blaze_rod",
                    keys.getAsJsonObject("B").get("item").getAsString());
            assertEquals("minecraft:crafting_table",
                    keys.getAsJsonObject("C").get("item").getAsString());
            assertEquals("minecraft:quartz",
                    keys.getAsJsonObject("Q").get("item").getAsString());
            assertEquals("tristorage:remote_tablet",
                    keys.getAsJsonObject("T").get("item").getAsString());
            assertEquals("tristorage:wireless_crafting_terminal",
                    recipe.getAsJsonObject("result").get("item").getAsString());
        }
    }
}
