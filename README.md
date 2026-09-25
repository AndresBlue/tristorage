# TriStorage

Mismo mod de almacenamiento en proyectos Gradle independientes, uno por
versión de Minecraft y loader. No hay multi-project: entra a un puerto y
usa su `gradlew`.

| Minecraft | Loader | Java | Proyecto | Release |
| --- | --- | --- | --- | --- |
| 1.16.5 | Fabric Loader 0.16.14 | 8 | [`fabric-1.16.5`](fabric-1.16.5) | [1.0](https://github.com/AndresBlue/tristorage/releases/tag/fabric-1.16.5-v1.0) |
| 1.18.2 | Fabric Loader 0.16.14 | 17 | [`fabric-1.18.2`](fabric-1.18.2) | [1.0](https://github.com/AndresBlue/tristorage/releases/tag/fabric-1.18.2-v1.0) |
| 1.20.1 | Fabric Loader 0.18.4 | 17 | [`fabric-1.20.1`](fabric-1.20.1) | [1.12.2](https://github.com/AndresBlue/tristorage/releases/tag/fabric-1.20.1-v1.12.2) |
| 1.21.1 | Fabric Loader 0.18.4 | 21 | [`fabric-1.21.1`](fabric-1.21.1) | [1.0](https://github.com/AndresBlue/tristorage/releases/tag/fabric-1.21.1-v1.0) |
| 1.21.11 | Fabric Loader 0.18.4 | 21 | [`fabric-1.21.11`](fabric-1.21.11) | [1.1](https://github.com/AndresBlue/tristorage/releases/tag/fabric-1.21.11-v1.1) |
| 1.21.1 | NeoForge 21.1.228 | 21 | [`neoforge-1.21.1`](neoforge-1.21.1) | [1.1](https://github.com/AndresBlue/tristorage/releases/tag/neoforge-1.21.1-v1.1) |

Los JAR compilados se publican como
[releases de GitHub](https://github.com/AndresBlue/tristorage/releases), una
por puerto y versión.

Cada puerto conserva las mismas mecánicas: cuatro tiers de núcleo, terminal,
linker, tableta remota, pestaña creativa, y acceso remoto entre dimensiones
o con el chunk descargado.

## Notas de compatibilidad

- Minecraft 1.16.5 no incluye cobre: pepitas de oro sustituyen cobre en las
  tres recetas auxiliares tempranas.
- Fabric API 0.42.0+1.16 usa el mod id histórico `fabric`; el manifiesto de
  ese puerto lo declara así.
- El puerto 1.21.11 usa components para el enlace de la tableta,
  ReadView/WriteView, rutas de data pack modernas y modelos de ítem actuales.
- Los tickets remotos en 1.21.11 usan conteo de referencias para que dos
  jugadores mantengan el mismo sistema abierto.
- El puerto Fabric 1.21.1 usa components para el enlace, NBT consciente del
  registro, y tickets remotos separados por terminal abierto.
- En 1.21.11 los contadores de ítem usan ARGB opaco.
- Desde la versión 1.9 del puerto 1.20.1, el Linker básico se limita al
  Overworld y la Antena Dimensional habilita el acceso entre dimensiones.

## Verificar un puerto

```powershell
cd fabric-1.20.1
.\gradlew.bat test build
```

Los class files finales usan major 52, 61 y 65 (Java 8, 17 y 21).

## Benchmark de almacenamiento (Fabric 1.20.1)

El puerto Fabric 1.20.1 incluye un generador de datos de estrés para
operadores. Apunta al bloque del Storage Core; la generación se ejecuta en
lotes limitados por tick y respeta la capacidad real del Core.

```text
/tristorage benchmark generate ~ ~ ~ 1000
/tristorage benchmark generate ~ ~ ~ 10000 mixed 12345
/tristorage benchmark generate ~ ~ ~ 10000 nbt 12345
/tristorage benchmark generate ~ ~ ~ 10000 heavy 12345
/tristorage benchmark generate ~ ~ ~ 1 bulk 12345
/tristorage benchmark status
```

Perfiles disponibles: `mixed` usa ítems registrados vanilla y de mods,
`nbt` crea variantes NBT del mismo ítem, `heavy` usa shulker boxes con NBT
interno y `bulk` concentra hasta un millón de unidades por tipo. Si se
solicitan más tipos o ítems que los disponibles, la tarea se detiene al
alcanzar el límite del Core y lo informa en el chat. El comando no borra
datos existentes; para comparaciones reproducibles usa una copia del mundo,
un Core vacío y una semilla explícita.
