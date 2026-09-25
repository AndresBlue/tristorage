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

    // Enables `//? if fabric {` style conditions in sources.
    constants {
        match(loader, "fabric", "forge", "neoforge")
    }
}
