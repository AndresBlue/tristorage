import org.gradle.api.file.CopySpec

/** Mixins whose targets only exist from a Minecraft version on, by that version. */
private val VERSIONED_MIXINS = mapOf(
    "ItemStackDataMixin" to "1.20.5",
)

/**
 * Leaves out of the mixin configs every mixin the version being built does
 * not have. Each one sits on its own line, so removing it keeps the JSON valid.
 */
fun CopySpec.filterVersionedMixins(minecraft: String) {
    val version = McVersion.of(minecraft)
    val absent = VERSIONED_MIXINS.filterValues { version < McVersion.of(it) }.keys
    if (absent.isEmpty()) {
        return
    }
    filesMatching("*.mixins.json") {
        filter { line -> if (absent.any { line.contains("\"$it\"") }) "" else line }
    }
}
