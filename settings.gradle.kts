pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Applies the Loom variant each Minecraft version needs.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Creates `versions/{minecraft}-{loader}` nodes built by `build.{loader}.gradle.kts`.
        fun match(minecraft: String, vararg loaders: String) {
            for (loader in loaders) {
                version("$minecraft-$loader", minecraft).buildscript("build.$loader.gradle.kts")
            }
        }

        match("1.20.1", "fabric", "forge")
        match("1.21.1", "fabric", "neoforge")
        vcsVersion = "1.20.1-fabric"
    }
}

rootProject.name = "tristorage"
