# TriStorage

Storage network mod (cores, terminals, linker, remote tablet). One codebase
builds every Minecraft version and loader through Stonecutter.

## Layout

| Path | What |
| --- | --- |
| `src/` | The only source tree. Mojang mappings. |
| `src/main/java/.../platform/` | `PlatformHooks`, `ClientPlatformHooks`, `Registrar`: the loader seam. |
| `src/main/java/.../platform/fabric/`, `platform/forge/`, `platform/neoforge/` | Loader code, wrapped in `//? if fabric {`, `//? if forge {` or `//? if neoforge {` blocks. |
| `src/main/java/.../version/` | Helpers for APIs that changed between Minecraft versions (`Stacks`, `SavedStacks`, `Ids`, `NbtFiles`, `Recipes`). Common code calls these. |
| `buildSrc/` | Build logic shared by the loader scripts: data pack upgrade per version, versioned mixins. |
| `stonecutter.properties.toml` | Mod and dependency versions per Minecraft version and loader. |
| `build.fabric.gradle.kts`, `build.forge.gradle.kts`, `build.neoforge.gradle.kts` | One build script per loader. |
| `versions/{minecraft}-{loader}/` | Generated Stonecutter nodes (build output only). |
| `docs/MULTIVERSION.md` | Plan and status of the unification. |
| `fabric-1.16.5/`, `fabric-1.18.2/`, `fabric-1.21.1/`, `fabric-1.21.11/`, `neoforge-1.21.1/` | Legacy ports being replaced. Read-only reference; do not edit unless asked. |

Mechanics stay in `storage/` first. Common code never imports loader
classes; it goes through `Platform.get()` / `ClientPlatform.get()`.

## Building

Run Gradle from this folder. The daemon runs on Java 25 (auto-provisioned from
`gradle/gradle-daemon-jvm.properties`); each node uses its own Java toolchain.

```powershell
.\gradlew.bat build
.\gradlew.bat :1.20.1-fabric:test
.\gradlew.bat :1.21.1-fabric:test
.\gradlew.bat :1.20.1-forge:runClient
```

Shared tests in `src/test` run on the Fabric nodes only.

The data pack in `src/main/resources/data/` is written in the 1.20.1 format;
the build converts it for newer versions. Do not add version-specific copies.

## Stonecutter

The active node is set in `stonecutter.gradle.kts` (`1.20.1-fabric`). Sources
on disk must match the active node: code for inactive loaders or versions stays
commented inside its `//? if ... {` block. Inside such blocks use line comments
only; a block comment would end the wrapper early.

To work on another version, run the `"Set active project to <node>"` task so
the sources switch to it; run `"Reset active project"` before committing.

Published JARs: GitHub releases on `AndresBlue/tristorage`. `dist/` is a local,
untracked copy.

This mod does not depend on Threat Director.
