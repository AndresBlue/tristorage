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

    // Plain renames in Minecraft 26.1, and in Fabric API when it took Mojang's
    // names with that version.
    val names26 = current.parsed >= "26.1"
    replacements {
        string(names26) { replace("ClickType", "ContainerInput") }
        string(names26) {
            replace("net.minecraft.client.renderer.LightTexture", "net.minecraft.util.LightCoordsUtil")
        }
        string(names26) { replace("LightTexture.FULL_BRIGHT", "LightCoordsUtil.FULL_BRIGHT") }
        string(names26) { replace("ServerWorldEvents", "ServerLevelEvents") }
        string(names26) { replace("ParticleFactoryRegistry", "ParticleProviderRegistry") }
        string(names26) { replace("handleInventoryMouseClick", "handleContainerInput") }
    }

    // Text components were classes with constructors until 1.19. Sources call
    // the factories on Component; older nodes get the constructors, by full
    // name so no import has to change.
    val componentClasses = current.parsed < "1.19"
    replacements {
        string(componentClasses) {
            replace("Component.literal(", "new net.minecraft.network.chat.TextComponent(")
        }
        string(componentClasses) {
            replace("Component.translatable(", "new net.minecraft.network.chat.TranslatableComponent(")
        }
        string(componentClasses) {
            replace("Component.empty()", "net.minecraft.network.chat.TextComponent.EMPTY.copy()")
        }
    }

    // HolderLookup.Provider arrived in 1.19.3, where RegistryAccess implements
    // it. Older nodes name RegistryAccess instead; the space after the dot is
    // legal Java and keeps the spelling unique, so switching back only
    // restores these and leaves real uses of RegistryAccess alone.
    val registryAccessLookup = current.parsed < "1.19.3"
    replacements {
        string(registryAccessLookup) {
            replace("HolderLookup.Provider", "net.minecraft.core. RegistryAccess")
        }
        string(registryAccessLookup) {
            replace("import net.minecraft.core.HolderLookup;", "import net.minecraft.core. RegistryAccess;")
        }
    }

    // Enables `//? if fabric {` style conditions in sources.
    constants {
        match(loader, "fabric", "forge", "neoforge")
        // `//? if emi {` wraps the EMI bridge; EMI is published up to 1.21.1.
        put("emi", version in setOf("1.20.1", "1.21.1"))
    }
}
