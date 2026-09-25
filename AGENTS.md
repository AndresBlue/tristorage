# TriStorage

Same storage network (core, terminal, linker, remote tablet) ported to
several Minecraft versions. Each child folder is a complete Gradle project.

Pick **one** port. Do not open the others.

| Port | Folder | Java |
| --- | --- | --- |
| Fabric 1.16.5 | `fabric-1.16.5/` | 8 |
| Fabric 1.18.2 | `fabric-1.18.2/` | 17 |
| Fabric 1.20.1 | `fabric-1.20.1/` | 17 |
| Fabric 1.21.1 | `fabric-1.21.1/` | 21 |
| Fabric 1.21.11 | `fabric-1.21.11/` | 21 |
| NeoForge 1.21.1 | `neoforge-1.21.1/` | 21 |

Published JARs: GitHub releases tagged `<port>-v<version>` on
`AndresBlue/tristorage`. `dist/` is a local, untracked copy. Index: `README.md`.

Typical packages: `block/`, `blockentity/`, `storage/`, `screen/`, `item/`,
`client/`, `network/`, `recipe/`. Mechanics stay in `storage/` first.

This mod does not depend on Threat Director.
