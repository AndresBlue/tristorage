plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Applies version- and loader-specific values from stonecutter.properties.toml.
    properties {
        tags(version, loader)
    }

    // Names Mojang's mappings changed in 1.21.11. Sources are written with the
    // old names; Stonecutter rewrites them for newer nodes and back again.
    // `.location()` is only ever called on a ResourceKey.
    val identifierNames = current.parsed >= "1.21.11"
    replacements {
        string(identifierNames) { replace("ResourceLocation", "Identifier") }
        string(identifierNames) { replace("import net.minecraft.Util;", "import net.minecraft.util.Util;") }
        string(identifierNames) { replace(".location()", ".identifier()") }
    }

    // Enables `//? if fabric {` style conditions in sources.
    constants {
        match(loader, "fabric", "forge", "neoforge")
        // `//? if emi {` wraps the EMI bridge; EMI is published up to 1.21.1.
        put("emi", version in setOf("1.20.1", "1.21.1"))
    }
}
