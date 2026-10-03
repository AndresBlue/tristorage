plugins {
    id("net.neoforged.moddev") version "2.0.147"
    // Lets only one NeoForge node decompile Minecraft at a time.
    id("neoforge-mutex")
}

val modId = sc.properties.get<String>("mod.id")
val modVersion = sc.properties.get<String>("mod.version")
val minecraft = sc.current.version
val neoForgeVersion = sc.properties.get<String>("deps.neoforge")

version = "$modVersion+mc$minecraft-neoforge"
base.archivesName = modId

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://repo.sleeping.town/", "EMI", "dev.emi")
    strictMaven("https://maven.blamejared.com/", "JEI", "mezz.jei")
}

neoForge {
    version = neoForgeVersion

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run/$minecraft-neoforge")
            // `-PquickPlayWorld=<save name>` opens that world directly.
            providers.gradleProperty("quickPlayWorld").orNull?.let {
                programArguments.addAll("--quickPlaySingleplayer", it)
            }
        }
        register("server") {
            server()
            // The server console is enough; no window.
            programArgument("--nogui")
            gameDirectory = rootProject.file("run/$minecraft-neoforge")
        }
    }
}

dependencies {
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
    // The dev server reads console commands typed into Gradle.
    matching { it.name == "runServer" }.configureEach {
        (this as JavaExec).standardInput = System.`in`
    }

    processResources {
        val props = mapOf(
            "version" to modVersion,
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            // Any build of this NeoForge line from the one compiled against.
            "neoforge" to neoForgeVersion.split('.').let { "[$neoForgeVersion,${it[0]}.${it[1].toInt() + 1})" },
        )
        inputs.properties(props)
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
        inputs.property("dataPackMinecraft", minecraft)
        upgradeDataPack(minecraft)
        filterVersionedMixins(minecraft)
        // NeoForge runs Mojang names; the refmap entry is only for Forge.
        filesMatching("*.mixins.json") {
            filter { line -> if (line.contains("\"refmap\"")) "" else line }
        }
        // NeoForge describes the mod's resource pack itself.
        exclude("fabric.mod.json", "META-INF/mods.toml", "pack.mcmeta")
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
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to the root build/libs/{mod version}/."
        from(jar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    }
}
