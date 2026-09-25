# TriStorage — NeoForge 1.21.1

Port nativo de TriStorage para:

- Minecraft 1.21.1
- NeoForge 21.1.228 o superior dentro de la rama 21.1.x
- Java 21

No requiere Fabric API, Fabric Loader ni Sinytra Connector.

Incluye los cuatro tiers del Storage Core, Storage Terminal, Crafting Terminal,
Storage Linker, Remote Access Tablet y Dimensional Antenna. La interfaz ofrece
búsqueda, categorías, ordenamiento, paginación y controles de inventario
vanilla, mientras que el almacenamiento conserva los componentes/NBT de cada
ítem y permite retirar/recolocar o mejorar un Core sin trasvasijar el contenido.

El acceso remoto usa leases temporales con conteo de referencias: solo carga los
chunks necesarios, libera los tickets al cerrar/desconectar y permite cruzar
dimensiones cuando el Linker tiene una antena activa. Los datos del port
NeoForge 1.0 (`InstalledChests`, `Entries`, `Stack`, `Count`) se migran al
formato actual automáticamente.

Todos los bloques de la red se extraen con cualquier pico. Al romper un Core,
su ítem conserva los cofres y el inventario almacenado para poder recolocarlo o
usarlo en la receta del tier siguiente.
