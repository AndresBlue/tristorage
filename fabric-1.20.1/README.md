# TriStorage

TriStorage es un mod Fabric para Minecraft 1.20.1 centrado en almacenamiento
grande, directo y sin multibloques arquitectónicos. Una red se forma colocando
de manera contigua un Núcleo, un Terminal y, opcionalmente, un Linker.

## Requisitos

- Minecraft 1.20.1
- Fabric Loader 0.14.22 o posterior (desarrollado y probado con 0.18.4)
- Fabric API 0.92.11+1.20.1
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

La **Wireless Crafting Terminal** mejora una Tableta normal con Netherite,
materiales del Nether y una mesa de crafteo. Conserva un enlace válido durante
la mejora y abre directamente la interfaz remota de almacenamiento y crafteo.

La Tableta funciona aunque el Linker esté en un chunk descargado. Sin mejoras,
el jugador y el sistema deben encontrarse en el Overworld. Para acceder desde
o hacia cualquier otra dimensión, abre la interfaz del Linker e instala una
**Antena Dimensional**. La antena necesita un bloque de aire adyacente: prioriza
la parte superior y se reorienta automáticamente si ese espacio se bloquea.

Todos los jugadores conectados al mismo Linker comparten una sola
sesión y un único grupo de tickets temporales no persistentes. Los chunks se
mantienen cargados mientras haya terminales remotos abiertos. Tras el último
cierre, las sesiones recientes permanecen calientes hasta cinco minutos para
evitar recargas repetidas. El presupuesto global de sesiones ociosas está
limitado a 16 chunks; si se supera, se libera primero la sesión menos reciente.

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
- `Q`: cuarzo del Nether
- `B`: vara de blaze

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

Wireless Crafting Terminal:

```text
E N E
B C B
Q T Q
```

- `E`: perla de Ender
- `N`: lingote de Netherite
- `B`: vara de blaze
- `C`: mesa de crafteo
- `Q`: cuarzo del Nether
- `T`: Tableta de Acceso Remoto

Antena Dimensional:

```text
Q G Q
R B R
Q C Q
```

## Seguridad y upgrades

Un Núcleo puede retirarse y mejorarse sin trasvasijar su contenido. El ítem
portátil conserva un UUID y un token exclusivo; los ítems permanecen en el
repositorio del mundo y vuelven a estar disponibles al colocar el Núcleo.
Clonar el NBT del ítem no duplica el almacenamiento: sólo el primer token válido
puede reclamarlo.

Los mundos anteriores se migran automáticamente desde InstalledChests y
Entries. El payload legacy no se elimina hasta que el snapshot inicial haya
sido confirmado en disco.

## Persistencia y rendimiento

- Cada storage posee un snapshot compacto y un journal incremental con CRC en
  world/data/tristorage/.
- Las mutaciones del mismo tick generan una sola revisión y una sola
  notificación para todos los terminales.
- Orden, búsqueda, categorías y páginas se mantienen mediante índices y vistas
  compartidas; una página envía como máximo 54 entradas.
- La carga del archivo se realiza fuera del server thread y se publica con un
  presupuesto fijo por tick.
- Descargar el chunk no destruye inmediatamente el runtime. Los storages
  inactivos se evacuan por LRU sólo bajo presión de memoria.
- Los Linkers siguen usando leases temporales; no se crean chunks forzados
  permanentes.

Administración:

~~~text
/tristorage metrics
/tristorage metrics reset
/tristorage metrics enable
/tristorage metrics disable
/tristorage metrics export
/tristorage inspect <x> <y> <z>
/tristorage selftest
/tristorage storages list
/tristorage storages inspect <uuid>
/tristorage storages recover <uuid> confirm
/tristorage storages purge <uuid> confirm
/tristorage benchmark generate <x> <y> <z> <types> [mixed|nbt|heavy|bulk] [seed]
/tristorage benchmark status
~~~

`/tristorage metrics export` escribe `world/data/tristorage/metrics.json`.
El generador de benchmark inserta datos de forma acotada (32 tipos por tick),
respeta la capacidad real del Core y permite reproducir datasets con una seed.

La matriz sintética automatizada se ejecuta con:

```powershell
.\gradlew.bat stressTest
```

El reporte queda en `build/reports/tristorage-stress/benchmark.json` e incluye
escalas de hasta 100.000 tipos, NBT pesado, journal, operaciones aleatorias y
ocho actores concurrentes.

## Build

```powershell
.\gradlew.bat clean test build
```

El JAR remapeado se genera en `build/libs/tristorage-mc1.20.1-1.12.1.jar`.
