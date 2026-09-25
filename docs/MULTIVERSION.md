# Unificación multiversión

Objetivo: que todas las versiones de TriStorage tengan las mismas funciones,
estética y comportamiento, construidas desde un solo código basado en
`fabric-1.20.1` (1.12.1), la versión más refinada.

## Decisiones

| Tema | Decisión |
| --- | --- |
| Base | `fabric-1.20.1` con los arreglos de la rama `fix/known-bugs-1.20.1` |
| Matriz | 8 builds (tabla siguiente) |
| 1.16.5 | Congelada en `fabric-1.16.5-v1.0`. Java 8 no puede compartir el código |
| Numeración | Un único número para todas las builds, empezando en 2.0.0 |
| JAR | `tristorage-<versión>+mc<minecraft>-<loader>.jar` |
| Releases | Una release de GitHub por versión con todos los JAR, tag `v<versión>` |
| Mundos existentes | Se migran desde el formato de cada puerto publicado |

| Minecraft | Loaders | Java |
| --- | --- | --- |
| 1.18.2 | Fabric | 17 |
| 1.20.1 | Fabric, Forge | 17 |
| 1.21.1 | Fabric, NeoForge | 21 |
| 1.21.11 | Fabric | 21 |
| 26.x | Fabric, NeoForge | 25 |

## Arquitectura

- Un solo proyecto Gradle 9 en `mods/tristorage/` con Stonecutter para las
  versiones y módulos por loader.
- Código común con la lógica: runtime, repositorio, planificadores, políticas,
  pantallas y bloques. Una capa de plataforma pequeña por loader para eventos
  de ciclo de vida, red, registros (pestaña creativa, block entities,
  partículas), comandos y carpeta de configuración.
- Mojang Mappings en todas las versiones. Desde 26.1 el juego no está ofuscado
  y Yarn ya no existe; además permite compilar contra la API real de JEI.
- Herramientas: Fabric Loom (`fabric-loom-remap` hasta 1.21.11, `fabric-loom`
  en 26.x), ModDevGradle para NeoForge y su plugin `legacyforge` para Forge
  1.20.1. Gradle descarga las toolchains de Java 17, 21 y 25.

## Fases

Cada fase termina con los tests en verde y un JAR probado. 1.20.1 sigue siendo
publicable durante todo el proceso.

1. **1.20.1 a Gradle 9 y Mojang Mappings.** Loom `fabric-loom-remap` 1.17.17,
   `migrateMappings`, revisión manual de mixins y del puente de JEI.
   Comportamiento idéntico.
2. **Stonecutter y Forge 1.20.1.** Nueva estructura del proyecto, capa de
   plataforma para los 11 archivos que usan la API de Fabric, y primer loader
   adicional.
3. **1.21.1 (Fabric, NeoForge).** Componentes de ítem en lugar de NBT, payloads
   de red con codecs, recetas con `MapCodec`, carpetas de datos en singular.
   Migración de mundos de `fabric-1.21.1` 1.0 y `neoforge-1.21.1` 1.1.
4. **1.21.11 (Fabric).** Recetas 1.21.2, definiciones de ítems 1.21.4,
   `ReadView`/`WriteView`, render states de 1.21.9, contadores ARGB y claves
   `item.*`. Migración de mundos de `fabric-1.21.11` 1.1.
5. **1.18.2 (Fabric).** Registros previos a 1.19.3, pestaña creativa antigua y
   GUI sin `DrawContext`. Migración de mundos de `fabric-1.18.2` 1.0.
6. **26.x (Fabric, NeoForge).** Juego sin ofuscar y Java 25.
7. **CI.** GitHub Actions compila y prueba la matriz y publica las releases.
8. **Retirada.** Las carpetas de los puertos antiguos salen de `main`; siguen en
   el historial y en sus releases.

## Formatos de guardado a migrar

| Origen | Formato del Core |
| --- | --- |
| `fabric-1.20.1` ≥ 1.9 | Repositorio del mundo (`data/tristorage/`), manifiesto y journal |
| `fabric-1.20.1` < 1.9, `fabric-1.16.5`, `fabric-1.18.2`, `fabric-1.21.1` 1.0 | `InstalledChests` y `Entries` en el block entity |
| `fabric-1.21.11` 1.1 | Componente `tristorage:core_storage` en ítem y block entity |
| `neoforge-1.21.1` 1.1 | `CoreStorageData`; ya migra los datos de Neo 1.0 |

La serialización de `ItemStack` cambia en 1.20.5 (componentes y registros), así
que las entradas guardadas deben convertirse al formato de cada versión.

## Aportes de otros puertos a conservar

- Test de minado de NeoForge (`BlockMiningResourcesTest`) y tags en
  `tags/block/` desde 1.21. Los puertos Fabric 1.21.1 y 1.21.11 publicados
  tienen `mineable/pickaxe` en `tags/blocks/`, que 1.21 ya no lee.
- Fuente de GUI aislada (`font/gui.json`) de 1.21.11 y NeoForge.
- Etiquetas "Inventory", "Stored Items" y "Dimensional Antenna" de NeoForge.
- Arte alternativo (tableta v1.9, hojas `_side`, singularidad): solo si se decide
  usarlo; la base es el arte de 1.20.1.

## Estado

- [x] Arreglos de 1.20.1 (`fix/known-bugs-1.20.1`)
- [x] Fase 1: Gradle 9.5.1, Loom 1.17.17 y Mojang Mappings. Tests, destinos de
  los mixins y una auditoría de las 237 sobrescrituras coinciden con la
  versión anterior. Falta probarlo en el juego.
- [ ] Fase 2
- [ ] Fase 3
- [ ] Fase 4
- [ ] Fase 5
- [ ] Fase 6
- [ ] Fase 7
- [ ] Fase 8
