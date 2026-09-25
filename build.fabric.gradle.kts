plugins {
    // Applies the Loom variant the current Minecraft version needs.
    id("dev.kikugie.loom-back-compat")
}

val modId = sc.properties.get<String>("mod.id")
val modVersion = sc.properties.get<String>("mod.version")
val minecraft = sc.current.version

version = "$modVersion+mc$minecraft-fabric"
base.archivesName = modId

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://repo.sleeping.town/", "EMI", "dev.emi")
    strictMaven("https://maven.blamejared.com/", "JEI", "mezz.jei")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Recipe viewers are optional; their plugins load only when the viewer is installed.
    compileOnly("dev.emi:emi-xplat-mojmap:${sc.properties.get<String>("deps.emi")}+$minecraft:api")
    compileOnly("mezz.jei:jei-$minecraft-common-api:${sc.properties.get<String>("deps.jei")}")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.fabricmc:fabric-loader-junit:${sc.properties.get<String>("deps.fabric_loader")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runConfigs.all {
        runDirectory = rootProject.file("run/$minecraft-fabric")
    }
    // `-PquickPlayWorld=<save name>` opens that world directly.
    providers.gradleProperty("quickPlayWorld").orNull?.let {
        runConfigs.named("client") { programArgs(listOf("--quickPlaySingleplayer", it)) }
    }
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
            "java" to requiredJava.majorVersion,
        )
        inputs.property("dataPackMinecraft", minecraft)
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
        upgradeDataPack(minecraft)
        filterVersionedMixins(minecraft)
        // Fabric remaps mixins without a refmap; the entry is only for Forge.
        filesMatching("*.mixins.json") {
            filter { line -> if (line.contains("\"refmap\"")) "" else line }
        }
        exclude("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta")
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = requiredJava.majorVersion.toInt()
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "1000"))
    }

    test {
        useJUnitPlatform {
            excludeTags("stress")
        }
    }

    register<Test>("stressTest") {
        description = "Runs TriStorage's high-volume storage stress benchmarks."
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        maxHeapSize = "2g"
        systemProperty(
            "tristorage.stress.output",
            layout.buildDirectory.file("reports/tristorage-stress/benchmark.json").get().asFile.absolutePath
        )
        useJUnitPlatform {
            includeTags("stress")
        }
        shouldRunAfter(test)
    }

    jar {
        from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to the root build/libs/{mod version}/."
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    }
}
