plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // Parses data pack JSON while it is upgraded for newer Minecraft versions.
    implementation("com.google.code.gson:gson:2.11.0")
}
