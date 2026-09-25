plugins {
    // Forge 1.17-1.20.1 through ModDevGradle's legacy plugin.
    id("net.neoforged.moddev.legacyforge") version "2.0.147"
}

val modId = sc.properties.get<String>("mod.id")
val modVersion = sc.properties.get<String>("mod.version")
val minecraft = sc.current.version
val mixinConfig = "$modId.client.mixins.json"

version = "$modVersion+mc$minecraft-forge"
base.archivesName = modId

val requiredJava = JavaVersion.VERSION_17

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://repo.sleeping.town/", "EMI", "dev.emi")
    strictMaven("https://maven.blamejared.com/", "JEI", "mezz.jei")
}

legacyForge {
    version = "$minecraft-${sc.properties.get<String>("deps.forge")}"

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run/$minecraft-forge")
            // `-PquickPlayWorld=<save name>` opens that world directly.
            providers.gradleProperty("quickPlayWorld").orNull?.let {
                programArguments.addAll("--quickPlaySingleplayer", it)
            }
        }
        register("server") {
            server()
            gameDirectory = rootProject.file("run/$minecraft-forge")
        }
    }
}

// Forge runs mixins against SRG names, so it needs the refmap Fabric does without.
mixin {
    add(sourceSets.main.get(), "$modId.refmap.json")
    config(mixinConfig)
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    // Recipe viewers are optional; their plugins load only when the viewer is installed.
    findProperty("deps.emi")?.let { compileOnly("dev.emi:emi-xplat-mojmap:$it+$minecraft:api") }
    compileOnly("mezz.jei:jei-$minecraft-common-api:${sc.properties.get<String>("deps.jei")}")
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        val props = mapOf(
            "version" to modVersion,
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
        )
        inputs.properties(props)
        filesMatching("META-INF/mods.toml") { expand(props) }
        inputs.property("dataPackMinecraft", minecraft)
        upgradeDataPack(minecraft)
        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    // The shared tests exercise loader-independent code and run on the Fabric nodes.
    named("compileTestJava") { enabled = false }
    named("test") { enabled = false }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    jar {
        from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
        manifest.attributes("MixinConfigs" to mixinConfig)
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to the root build/libs/{mod version}/."
        dependsOn("reobfJar")
        from(layout.buildDirectory.file("libs/$modId-$version.jar"))
        into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    }
}
