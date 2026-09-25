import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.gradle.api.file.CopySpec
import java.io.FilterReader
import java.io.Reader
import java.io.StringReader

/**
 * The data pack is written once, in the Minecraft 1.20.1 format. This copies
 * it into the format of the version being built: folder names and the JSON
 * shapes that changed along the way.
 */
fun CopySpec.upgradeDataPack(minecraft: String) {
    filesMatching(listOf("data/*/recipes/**/*.json", "data/*/loot_tables/**/*.json")) {
        filter(mapOf("minecraft" to minecraft), DataPackUpgrade::class.java)
    }
    if (McVersion.of(minecraft) >= McVersion.of("1.21")) {
        // 1.21 singularized the data pack folders.
        eachFile {
            path = path
                .replace(Regex("^data/([^/]+)/recipes/"), "data/$1/recipe/")
                .replace(Regex("^data/([^/]+)/loot_tables/"), "data/$1/loot_table/")
                .replace(Regex("^data/([^/]+)/tags/blocks/"), "data/$1/tags/block/")
                .replace(Regex("^data/([^/]+)/tags/items/"), "data/$1/tags/item/")
        }
        includeEmptyDirs = false
    }
}

/** Rewrites one recipe or loot table; Gradle sets [minecraft] before reading. */
class DataPackUpgrade(input: Reader) : FilterReader(input) {
    var minecraft: String = ""
    private var upgraded: Reader? = null

    private fun upgraded(): Reader = upgraded ?: StringReader(upgrade(`in`.readText())).also { upgraded = it }

    override fun read(): Int = upgraded().read()

    override fun read(buffer: CharArray, offset: Int, length: Int): Int =
        upgraded().read(buffer, offset, length)

    override fun ready(): Boolean = true

    private fun upgrade(text: String): String {
        val version = McVersion.of(minecraft)
        val root = JsonParser.parseString(text)
        if (!root.isJsonObject || version < McVersion.of("1.20.5")) {
            return text
        }
        val json = root.asJsonObject
        if (json.has("result") && json.has("type")) {
            upgradeRecipe(json)
        }
        if (json.has("pools")) {
            upgradeLootTable(json)
        }
        return GSON.toJson(json) + "\n"
    }

    /**
     * 1.20.5 renamed the result's item field to id; 1.21.2 writes each
     * ingredient as an item id, a #tag or a list of item ids.
     */
    private fun upgradeRecipe(json: JsonObject) {
        val result = json.get("result")
        if (result is JsonObject && result.has("item")) {
            result.add("id", result.remove("item"))
        }
        if (McVersion.of(minecraft) >= McVersion.of("1.21.2")) {
            json.getAsJsonObject("key")?.let { key ->
                key.entrySet().toList().forEach { (symbol, value) -> key.add(symbol, ingredient(value)) }
            }
            json.getAsJsonArray("ingredients")?.let { list ->
                for (index in 0 until list.size()) {
                    list.set(index, ingredient(list.get(index)))
                }
            }
        }
    }

    private fun ingredient(value: JsonElement): JsonElement = when {
        value is JsonObject && value.has("item") -> JsonPrimitive(value.get("item").asString)
        value is JsonObject && value.has("tag") -> JsonPrimitive("#" + value.get("tag").asString)
        value is JsonArray -> JsonArray().apply { value.forEach { add(ingredient(it)) } }
        else -> value
    }

    /**
     * 1.20.5 moved block entity data out of item NBT, so copy_nbt into
     * BlockEntityTag becomes copying the block_entity_data component.
     */
    private fun upgradeLootTable(json: JsonObject) {
        forEachObject(json) { function ->
            if (function.get("function")?.asString == "minecraft:copy_nbt"
                && function.getAsJsonArray("ops")?.all {
                    it.asJsonObject.get("target").asString.startsWith("BlockEntityTag.")
                } == true
            ) {
                function.entrySet().clear()
                function.addProperty("function", "minecraft:copy_components")
                function.addProperty("source", "block_entity")
                function.add("include", JsonArray().apply { add("minecraft:block_entity_data") })
            }
        }
    }

    private fun forEachObject(element: JsonElement, action: (JsonObject) -> Unit) {
        when (element) {
            is JsonObject -> {
                action(element)
                element.entrySet().map { it.value }.forEach { forEachObject(it, action) }
            }
            is JsonArray -> element.forEach { forEachObject(it, action) }
            else -> {}
        }
    }

    private companion object {
        val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    }
}

/** A Minecraft release number such as 1.20.1 or 26.1, compared numerically. */
data class McVersion(val parts: List<Int>) : Comparable<McVersion> {
    override fun compareTo(other: McVersion): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(index) { 0 } - other.parts.getOrElse(index) { 0 }
            if (difference != 0) {
                return difference
            }
        }
        return 0
    }

    companion object {
        fun of(version: String) = McVersion(version.split('.').map { it.takeWhile(Char::isDigit).toInt() })
    }
}
