# TriStorage 1.1 — Fabric 1.21.11

Port de TriStorage para Minecraft 1.21.11. Mantiene el sistema compacto de
Núcleo, Terminal y Linker, incorporando las mejoras modernas de inventario,
crafting y acceso remoto.

## Requisitos

- Minecraft 1.21.11
- Fabric Loader 0.18.4 o posterior
- Fabric API 0.141.5+1.21.11
- Java 21

## Almacenamiento

Cada cofre normal instalado en el Núcleo aporta 27 tipos de ítem y 1.728
unidades. Los cuatro tiers alcanzan desde 27 hasta 13.824 cofres instalados.

Los Núcleos conservan cofres e ítems al romperlos. Sus datos también sobreviven
al utilizarlos en la receta del tier siguiente, evitando vaciar el sistema para
hacer un upgrade.

## Terminales

El Terminal incluye:

- búsqueda por nombre o identificador;
- categorías inspiradas en las pestañas del creativo y categorías por mod;
- navegación por rueda, flechas, páginas y categorías;
- depósito masivo, orden por ID o cantidad;
- shift-clic y doble clic para transferencias rápidas;
- ajustes de búsqueda, categorías y rueda persistidos en el cliente.
- umbral configurable para mostrar las categorías solo en sistemas grandes.

El Crafting Terminal integra una matriz 3×3 y permite retirar ingredientes del
almacenamiento sin cerrar la interfaz.

## Acceso remoto

La Tableta se enlaza usando el Linker. La carga remota se solicita mediante
tickets compartidos y se completa de forma diferida, sin forzar una carga
síncrona del chunk durante el tick que procesa el clic.

El acceso funciona en el Overworld de forma predeterminada. Para acceder desde
o hacia otras dimensiones se debe instalar una Antena Dimensional en la interfaz
del Linker.

## Presentación 1.9

Este port conserva la presentación completa de TriStorage 1.9: interfaces con
estilo vanilla, fuente aislada y compatible con resource packs, texturas de
bloque animadas, tablet de 32 px, modelos superiores diferenciados y el soporte
dimensional 3D. La antena despliega una singularidad animada que siempre mira al
jugador, partículas de portal y End convergentes e ítems reales del sistema en
órbitas escalonadas de generación y absorción.

## Build

```powershell
.\gradlew.bat clean test build
```

El JAR remapeado se genera en
`build/libs/tristorage-mc1.21.11-1.1.jar`.
