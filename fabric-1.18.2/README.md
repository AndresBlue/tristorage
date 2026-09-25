# TriStorage

TriStorage es un mod Fabric para Minecraft 1.20.1 centrado en almacenamiento
grande, directo y sin multibloques arquitectónicos. Una red se forma colocando
de manera contigua un Núcleo, un Terminal y, opcionalmente, un Linker.

## Requisitos

- Minecraft 1.18.2
- Fabric Loader 0.16.14
- Fabric API 0.77.0+1.18.2
- Java 17

## Uso

1. Coloca un Núcleo de Almacenamiento.
2. Abre el Núcleo e inserta cofres normales en la ranura central. Cada cofre
   agrega 27 tipos distintos y 1.728 unidades totales.
3. Coloca el Terminal tocando cualquier pieza de la red contigua. Ábrelo para
   depositar o retirar ítems:
   - clic izquierdo: deposita el cursor completo o retira un stack;
   - clic derecho: deposita o retira una unidad;
   - shift-clic desde el inventario: deposita el stack;
   - shift-clic en la red: extrae al inventario.
4. Coloca el Linker en el mismo grupo contiguo. Usa una Tableta sobre él para
   enlazarla y luego usa la Tableta en el aire para abrir el Terminal remoto.

La Tableta funciona aunque el Linker esté en un chunk descargado o en otra
dimensión. Mientras la Terminal remota esté abierta, TriStorage mantiene ese
chunk cargado mediante un ticket temporal no persistente y lo libera al cerrar.

## Tiers

| Tier | Cofres | Tipos máximos | Ítems máximos |
| --- | ---: | ---: | ---: |
| I | 27 | 729 | 46.656 |
| II | 432 | 11.664 | 746.496 |
| III | 1.728 | 46.656 | 2.985.984 |
| IV | 13.824 | 373.248 | 23.887.872 |

## Recetas auxiliares

Las letras representan:

- `G`: cristal o panel de cristal según la receta
- `I`: lingote de hierro
- `R`: redstone
- `C`: lingote de cobre
- `H`: cofre
- `T`: antorcha de redstone
- `N`: pepita de hierro

Terminal:

```text
G I G
R H R
I C I
```

Linker:

```text
G T G
C R C
I I I
```

Tableta:

```text
N G N
C R C
N N N
```

## Seguridad y upgrades

Un Núcleo con ítems o cofres instalados no puede romperse en supervivencia.
Vacía primero el contenido desde el Terminal y usa **Retirar 64** en la interfaz
del Núcleo hasta recuperarlos todos. Después puedes minarlo y usarlo en la
receta del tier siguiente sin riesgo de perder datos.

## Build

```powershell
.\gradlew.bat clean test build
```

El JAR remapeado se genera en `build/libs/tristorage-mc1.18.2-1.0.jar`.
