# Unificación multiversión

Objetivo: que todas las versiones de TriStorage tengan las mismas funciones,
estética y comportamiento, construidas desde un solo código basado en
`fabric-1.20.1` (1.12.1), la versión más refinada.

## Decisiones

| Tema | Decisión |
| --- | --- |
| Base | `fabric-1.20.1` con los arreglos de la rama `fix/known-bugs-1.20.1` |
| Matriz | 10 builds (tabla siguiente) |
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
| 26.1.2 | Fabric, NeoForge | 25 |
| 26.2 | Fabric, NeoForge | 25 |

26.3 queda fuera por ahora: NeoForge solo tiene betas para esa versión y el
juego cambió más cosas (soltar ítems, matrices de render, teclado).

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

Para probar un nodo sin abrir el juego, su servidor de desarrollo corre sin
ventana y acepta comandos por la consola de Gradle:

```powershell
.\gradlew.bat :26.2-fabric:runServer
```

`/forceload add <x> <z>` carga el chunk de un Core y `/tristorage inspect <x>
<y> <z>` muestra si quedó listo y cuánto guarda. Desde 1.21.2 un servidor vacío
se pausa al minuto (`pause-when-empty-seconds`), y un Core no termina de cargar
en pausa.

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
| Todos los demás puertos, Core colocado | `InstalledChests` y `Entries` en el block entity |
| `neoforge-1.21.1` 1.x, Core como ítem | `custom_data`, directo o bajo `TriStorageCore` |
| `fabric-1.21.11` 1.x, Core como ítem | Componente propio `tristorage:core_storage` |

Todos se leen. El Core colocado entra por la carga legacy del block entity y
pasa al repositorio. El ítem se lee donde esté y se reescribe como
`block_entity_data` al colocarlo; el componente `tristorage:core_storage` sigue
registrado desde 1.21.11 solo para que esos ítems carguen. Las tabletas de todos
los puertos usan las mismas claves, y un Linker del puerto 1.21.11 guardado como
ítem con su antena la recupera al colocarlo.

La serialización de `ItemStack` cambia en 1.20.5 (componentes y registros), así
que las entradas guardadas deben convertirse al formato de cada versión.

### Cómo se migra entre versiones de Minecraft

- Cada stack que TriStorage guarda fuera de los slots de vanilla (repositorio,
  `Entries` del block entity, datos del Core portátil) va con el
  `DataVersion` del juego que lo escribió. Al leerlo, `SavedStacks` lo pasa
  por los data fixers de vanilla.
- Los datos anteriores a 2.0 no tienen versión, y nada dice qué juego los
  escribió. `SavedStacks` prueba las versiones de los puertos publicados, de
  la más nueva a la más vieja (1.20.1, 1.18.2 y 1.16.5 para el formato con
  `Count`; 1.21.11 y 1.21.1 para el de componentes), y se queda con la primera
  cuyo resultado el juego acepta completo. Aplicar fixers a datos que ya son
  nuevos borra encantamientos y nombres; no aplicarlos a datos viejos pierde
  ítems renombrados. Un nombre o lore guardado como JSON en una cadena delata
  el formato anterior a 1.21.5.
- El ítem de Core del puerto 1.21.11 se sabe escrito por 1.21.11 y lleva esa
  versión al convertirse.
- El fixer de 1.20.5 mueve `BlockEntityTag` al componente
  `block_entity_data`, que exige un `id`. Los Cores soltados por la loot table
  de 1.20.1 antes de 2.0 no lo tenían y 1.21 descartaría el ítem con todo su
  contenido. `ItemStackDataMixin` agrega el `id` antes de ese fixer, y la loot
  table de 1.20.1 ahora copia `id` y `TriStorageDataVersion`.

## Diferencias entre versiones en el código

- `version/`: `Stacks` (NBT o componentes), `SavedStacks` (data fixers),
  `Ids`, `NbtFiles` y `Recipes`. El código común llama a estos helpers en vez
  de usar la API de una versión. `GameRegistries` (registros antes y
  después de 1.19.3), `CreativeTabs` (pestañas creativas como categorías) y
  `Menus` (rejilla de crafteo y ranuras) cubren lo que cambió antes de 1.20.
- `recipe/WrappedShapedRecipe`: base de las recetas propias; oculta el cambio
  de serializador (JSON y `FriendlyByteBuf` hasta 1.20.4, `MapCodec` y
  `StreamCodec` desde 1.21).
- Red: los paquetes siguen escribiéndose como bytes. Desde 1.20.5 cada
  dirección usa un único payload tipado con el canal y esos bytes
  (`ChannelPayload`); Fabric y NeoForge solo lo registran a su manera.
- Nombres que Mojang cambió en 1.21.11 (`ResourceLocation` → `Identifier`,
  `net.minecraft.Util`, `ResourceKey.location()`): el código se escribe con
  los nombres viejos y `stonecutter.gradle.kts` los reemplaza en los nodos
  nuevos.
- NBT (`Nbt`), block entities con `ValueOutput`/`ValueInput` (`ValueTags`),
  jugador y servidor (`Players`) y recetas sin `getIngredients` (`Recipes`)
  pasan por helpers de `version/`. Los colores de texto de la GUI llevan alfa
  (`0xFF…`), que 1.21.6 exige y las versiones anteriores aceptan.
- Dibujo de la GUI: las pantallas extienden `client/CanvasScreen` y dibujan
  con `client/Canvas`, que usa `PoseStack` antes de 1.20, `GuiGraphics`
  después y las matrices 2D de 1.21.6. `client/Widgets` crea botones y
  tooltips como los pide cada versión. Las pantallas no tienen condicionales
  de dibujo.
- Textos y registros previos a 1.19: el código usa `Component.literal`,
  `Component.translatable` y `HolderLookup.Provider`; `stonecutter.gradle.kts`
  los reemplaza por `TextComponent`, `TranslatableComponent` y `RegistryAccess`
  en los nodos anteriores.
- Pestaña creativa: desde 1.20 se registra; antes se crea al inicio y cada
  ítem la nombra (`TriStorageMod.createCreativeTab`).
- Minecraft 26.1 renombró clases y métodos sin cambiar su uso (`ClickType` →
  `ContainerInput`, `LightTexture` → `LightCoordsUtil`, y en Fabric API
  `ServerWorldEvents` → `ServerLevelEvents`, `ParticleFactoryRegistry` →
  `ParticleProviderRegistry`): también son reemplazos de
  `stonecutter.gradle.kts`. Lo que cambió de forma (dibujo `extract*` con
  `GuiGraphicsExtractor`, tamaño de pantalla fijo en el constructor, mensajes
  sobre la barra, `ChunkPos` como record) pasa por `Canvas`, `CanvasScreen`,
  `Players.actionBar` y `version/Chunks`.
- Recetas en 26.1: el serializador es un record con los dos codecs y el de una
  receta con forma debe estar tipado como `ShapedRecipe`;
  `WrappedShapedRecipe.serializer` lo construye así. El resultado se guarda
  como `ItemStackTemplate` porque las recetas cargan antes de que los ítems
  tengan componentes.
- Tests: `TestStacks.bootstrap()` inicia el juego una vez; desde 26.1 además
  aplica los componentes de los ítems, que el juego ya no asigna al crearlos.
- Pantallas: `TriStorageClient.registerScreens` recibe el registro del
  loader (`MenuScreens::register` en Fabric y Forge,
  `RegisterMenuScreensEvent` en NeoForge, donde ese método es privado).
- Datos: el data pack se escribe una vez, en formato 1.20.1. `buildSrc`
  (`upgradeDataPack`) lo convierte al empaquetar: carpetas en singular desde
  1.21, `result.id` en recetas y `copy_components` en la loot table del Core
  desde 1.20.5. Los mixins que solo existen desde cierta versión se quitan de
  la configuración con `filterVersionedMixins`.

## Aportes de otros puertos

- Test de minado de NeoForge: `BlockMiningResourcesTest` corre en todos los
  nodos Fabric sobre el data pack ya convertido, con los tags en `tags/block/`
  desde 1.21. Los puertos Fabric 1.21.1 y 1.21.11 publicados tienen
  `mineable/pickaxe` en `tags/blocks/`, que 1.21 ya no lee.
- Fuente de GUI aislada de 1.21.11 y NeoForge: `assets/tristorage/font/gui.json`
  apunta a la fuente por defecto y `Canvas` dibuja todo su texto con ella, así
  un resource pack puede cambiar solo la tipografía de TriStorage. Desde 1.20;
  1.18.2 no permite que una fuente refiera a otra y usa la de siempre.
- Etiquetas "Inventory", "Stored Items" y "Dimensional Antenna" de NeoForge:
  estaban en el archivo de idioma de ese puerto, pero ninguna pantalla las
  mostraba. No hay nada que traer.
- Arte alternativo (tableta v1.9, hojas `_side`, singularidad): no se adoptó;
  la base es el arte de 1.20.1.

## Estado

- [x] Arreglos de 1.20.1 (`fix/known-bugs-1.20.1`)
- [x] Fase 1: Gradle 9.5.1, Loom 1.17.17 y Mojang Mappings. Tests, destinos de
  los mixins y una auditoría de las 237 sobrescrituras coinciden con la
  versión anterior. Falta probarlo en el juego.
- [x] 1.12.2 publicada para 1.20.1 con todos los arreglos
- [x] Fase 2: estructura Stonecutter (Gradle 9.8, Loom 1.18.2, ModDevGradle
  2.0.147 legacyforge, daemon en Java 25), capa de plataforma, APIs reales de
  JEI y EMI, y nodos `1.20.1-fabric` y `1.20.1-forge`. Los 65 tests pasan en
  Fabric. Ambos clientes llegan al menú, cargan un mundo de desarrollo de
  Fabric y se apagan sin errores. Falta probar en el juego abrir terminales y
  la tableta en Forge.
- [x] Fase 3: nodos `1.21.1-fabric` y `1.21.1-neoforge` (NeoForge 21.1.251,
  ModDevGradle 2.0.147). Los 75 tests pasan en 1.20.1 y en 1.21.1. Los
  clientes Fabric y NeoForge de 1.21.1 abren un mundo de 1.20.1 con un Core
  de 80 tipos y 941 ítems y lo guardan en el formato nuevo sin perder nada.
  Los block entities de `fabric-1.21.1` 1.0 y `neoforge-1.21.1` 1.x usan las
  mismas claves (`InstalledChests`, `Entries`) y entran por la carga legacy;
  los Cores portátiles de NeoForge (`custom_data`, con o sin
  `TriStorageCore`) se leen y pasan a `block_entity_data` al colocarlos.
  Falta probar en el juego terminales, tableta y un Core portátil de
  NeoForge 1.x.
- [x] Fase 4: nodo `1.21.11-fabric` (Fabric API 0.141.6, JEI 27; EMI no se
  publica para 1.21.11 y su puente queda fuera con `//? if emi {`). Los 75
  tests pasan en los tres nodos Fabric. El cliente carga sin errores; falta
  abrir un mundo y probar la interfaz en el juego. Arreglado de paso: las
  sesiones remotas cargaban un radio de 33 chunks por Linker y Core.
- [x] Fase 5: nodo `1.18.2-fabric` (Fabric API 0.77.0, JEI 10.2.1; EMI solo publicó
  versiones 0.1 para 1.18.2 y su puente queda fuera). Los 75 tests pasan en
  los cuatro nodos Fabric. Un servidor dedicado de 1.18.2 arranca con el mundo
  del puerto `fabric-1.18.2` 1.0, carga las recetas y responde a
  `/tristorage`. Los Cores de ese puerto usan `InstalledChests` y `Entries`
  y entran por la carga legacy. Falta probar el cliente en el juego: pantallas,
  render del Linker y partículas.
- [x] Fase 6: nodos `26.1.2-fabric`, `26.1.2-neoforge`, `26.2-fabric` y
  `26.2-neoforge` (Fabric API 0.155.3 y 0.161.0, NeoForge 26.1.2.114 y
  26.2.0.88, JEI 29 y 30; sin EMI). Los 75 tests pasan en los seis nodos
  Fabric. Un servidor dedicado de cada nodo 26.x, y el de 1.21.11, abre una
  copia del mundo de 1.20.1 y deja el Core con sus 27 cofres, 80 tipos y 941
  ítems, reescrito con el `DataVersion` nuevo. Falta probar el cliente en el
  juego en todas las versiones 26.x.
- [x] Migración de los puertos 1.x: el mundo del puerto `fabric-1.21.11` (dos
  Cores, 46 y 13 tipos) abre en 1.21.11 y directo en 26.2 sin errores ni
  ítems descartados. Tests para el ítem con `tristorage:core_storage`, para
  stacks sin versión de 1.21.1 y de 1.21.11, y para el formato del puerto
  NeoForge. No hay mundos de prueba de `fabric-1.21.1` ni de `neoforge-1.21.1`
  con Cores; esos formatos solo están cubiertos por tests.
- [x] Aportes de otros puertos: test de minado y fuente de GUI.
- [x] Servidores dedicados de los diez nodos: los nueve desde 1.20.1 abren
  una copia del mundo de 1.20.1 y dejan el Core listo con 27 cofres, 80 tipos
  y 941 ítems; el de 1.18.2 arranca con el mundo de su puerto 1.0, que no
  tiene Cores. Ningún cliente 2.0 se ha probado jugando: pantallas, render del
  Linker, partículas, JEI y EMI siguen sin verificar en el juego.
- [ ] Fase 7
- [ ] Fase 8
