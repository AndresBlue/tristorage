# TriStorage changelog

## 1.12.1 — Minecraft 1.20.1 Fabric

- Fixed JEI/EMI recipe availability occasionally reporting stored ingredients
  as missing, including recipes whose ingredients were outside the visible page.
- Recipe-viewer availability now falls back to a complete authoritative lookup
  when a fast indexed lookup cannot prove that the recipe is craftable.
- Fixed category icons overlapping or losing their frames after repeated recipe
  fills by preserving EMI's live widgets and isolating third-party item renderers.
- Wireless terminals now resolve fully loaded Linker/Core networks directly,
  without entering the cold chunk-loading pipeline.
- Creative-tab metadata is prepared during world loading and cached until a
  datapack reload, removing that large-pack cost from the first tablet open.

## 1.12 — Minecraft 1.20.1 Fabric

- Crafting Terminals now automatically refill consumed grid ingredients from
  storage while preserving exact item variants, NBT and vanilla recipe remainders.
- Shift-click crafting can continuously refill and craft up to one output stack,
  or as much as the available ingredients allow.
- Wireless terminals now open through a true loaded-chunk fast path, avoiding
  redundant asynchronous scheduling and loading messages for warm systems.
- Reduced multiplayer refresh work by reusing active views and item templates
  when only stored quantities change.
- Added incremental category membership indexes and faster broad-search view
  construction for very large storages.
- Reworked recipe availability matching to avoid exponential searches and added
  server-side request validation/rate limiting for JEI and EMI integrations.
- Fixed category-tab render-layer corruption by batching themed frames and item
  icons in stable passes, with cached mod/category icons.
- Repository-backed item movements no longer dirty and resave redundant Core
  BlockEntity data on every mutation.

## 1.11 — Minecraft 1.20.1 Fabric

- Added the Wireless Crafting Terminal, a post-Nether upgrade that opens the
  complete Crafting Terminal from anywhere.
- Linked Remote Access Tablets keep their validated Linker address when
  upgraded, without copying unrelated item NBT.
- Wireless and standard tablets share the same optimized remote chunk session
  while retaining separate server-authoritative capabilities.
- Remote crafting grids safely return their ingredients to storage, player
  inventory or the world when access closes unexpectedly.
- Redesigned both tablets with compact, stable 32×32 animated sprites and
  cleaner GUI pixel density.
- Added native JEI and EMI recipe transfer support for both physical and
  wireless Crafting Terminals. Recipe fills now resolve ingredients against
  the complete storage, regardless of the visible page, search or category.

## 1.10 — Minecraft 1.20.1 Fabric

- Improved simultaneous item transfers for multiple players by consolidating
  terminal updates to one refresh per server tick.
- Reduced Remote Access Tablet reopening delays by keeping recent sessions warm
  for five minutes instead of 30 seconds.
- Added an adaptive 16-chunk budget for idle remote sessions, releasing the
  least-recently used session first instead of permanently loading chunks.
- Linker and known Storage Core chunks are now requested in parallel during a
  cold remote open.
- Moved journal and item NBT serialization away from the server thread, reducing
  stuttering during large or rapid storage mutations.
- Precomputed search metadata during bounded loading and insertion, making the
  first search or category selection substantially faster on large storages.
- Improved remote diagnostics for successful, cancelled, failed and timed-out
  requests, chunk loading and ticket cleanup.
- Added repeatable stress coverage for up to 100,000 item types, eight
  concurrent users, 100,000 randomized operations, heavy NBT and journal
  recovery.

## 1.9 — Minecraft 1.20.1 Fabric

- Moved storage ownership out of chunk BlockEntities into a world-level runtime repository.
- Added atomic snapshots and CRC-protected incremental journals, eliminating full-catalog chunk saves after migration.
- Existing InstalledChests/Entries cores migrate automatically and retain exact NBT item variants.
- Added stable storage and entry IDs so stale multiplayer clicks can never withdraw a different item.
- Virtual terminal actions now validate the storage UUID, entry ID, page revision and active remote lease server-side.
- Replaced full re-sorts with incremental registry, count, insertion and recent indexes.
- Added shared filtered views, cursor-assisted pages and one consolidated storage revision per server tick.
- Players on the same filter/page now share one immutable page snapshot instead of rebuilding it per handler.
- Repository files load off-thread and publish at a bounded rate while the remote terminal displays its existing loading state.
- Recently unloaded storages stay warm in an LRU cache; inactive portable runtimes can be evicted under a bounded memory budget.
- Portable cores now carry a world-backed UUID and exclusive ownership token instead of copying the complete catalog into the item.
- Remote sessions cache valid topology, wait for repository readiness and release access when the linked tablet is lost.
- Added /tristorage metrics, metrics reset and metrics export for server-side performance diagnostics.
- Added explicit repository list, inspect, portable recovery and empty-only purge commands for administrators.
- Legacy catalogs are converted and published in bounded per-tick slices instead of rebuilding every ItemStack in one tick.
- Fixed antenna texture bleeding and top-face flickering where its base meets the Linker.
- Expanded block animations to smoother 12-frame cycles and reduced their playback speed by about half.
- Added full-strip travelling lights to Storage Cores, orbiting Terminal lights and slow purple Linker data flow.
- Rebuilt the Remote Access Tablet as a detailed 32×32 animated item texture.
- Reduced the tablet's in-slot footprint so its frame and animation no longer crowd the item slot.
- Rebuilt the Dimensional Antenna as a segmented cosmic receiver with an angled satellite panel and clean material separation.
- Added a GUI-only tablet scale so the hotbar icon is smaller without changing its first-person appearance.
- Replaced the satellite panel with a clean vertical antenna ending in an animated miniature singularity.
- Added orientation-aware Nether portal particles that spiral toward the antenna tip.
- Rebuilt the antenna as a four-pronged receptacle inspired by crystal-defense stands.
- Replaced the solid singularity cube with intersecting translucent energy planes and a darker layered core.
- Greatly increased the portal-particle accretion ring and added inward motion with a subtle orbit.
- Lowered the receptacle into an open spider-like ring with no central post.
- Portal particles now approach the singularity from a full sphere instead of a flat ring.
- Added tiny orbiting item projections: stable satellites circle the core while other items spawn outside, spiral inward, shrink and are absorbed.
- Redesigned every TriStorage block with more detailed technology-themed pixel art and subtle animated energy circuits.
- Added the Dimensional Antenna, a Nether-tech Linker upgrade with its own crafting recipe and inventory slot.
- Basic Linkers now provide remote access only when both the player and storage system are in the Overworld.
- An active Dimensional Antenna unlocks remote access from and to every dimension.
- The antenna appears physically on the Linker, prefers an open space above it and automatically reorients around nearby obstructions.
- Removing or obstructing the antenna immediately revokes dimensional sessions without deleting the installed item.
- The Linker now has a dedicated vanilla-style configuration screen and safely returns its antenna when broken.

## 1.8 — Minecraft 1.20.1 Fabric

- Added previous/next category buttons for players without a reliable mouse wheel.
- Remote users connected to the same storage now share one warm session, chunk lease and network scan.
- Already-loaded chunks are reused immediately instead of scheduling another asynchronous load.
- Repeated tablet requests are deduplicated across players and protected by bounded warm-session eviction.
- Remote chunks remain loaded only while the system is in use and for 30 seconds after the last user closes it.
- Bulk deposits, withdrawals and crafting-input returns now commit as one storage mutation.
- Amount-only changes preserve stable registry, insertion and recent-order indexes.
- Open filtered views update cached totals incrementally when item types do not change.
- Creative-tab membership is preindexed once instead of rescanning every item group during inventory refreshes.
- Reduced redundant page refreshes during Shift-click and multiplayer transfers.

## 1.7 — Minecraft 1.20.1 Fabric

- Added a compact, debounced search bar to Storage and Crafting Terminals.
- Search now matches item names, custom names, translation keys, registry IDs, namespaces and mod names without optional dependencies.
- Added vanilla creative-inventory category tabs with their real icons and full names, including tabs registered by other mods.
- Mod grouping now uses each mod's creative-tab icon when available.
- Added a configurable category threshold for small and very large storage systems.
- Mouse-wheel navigation now changes storage pages, or cycles categories while hovering their tabs, without triggering inventory-transfer mods.
- Added a gear tab with persistent client settings for search, categories, category mode, wheel navigation and threshold; its overlay now fully covers and locks the inventory beneath it.
- The inventory key is ignored while the search field is focused, and category tabs show their current position (for example, 2/17).
- Improved category-position contrast so the counter remains readable over vanilla world backgrounds.
- Filtering remains server-authoritative, paginated and cached to prevent inventory desynchronization or large unnecessary item transfers.
- Preserved Shift-click transfers, sorting, remote access and compatibility with item variants carrying NBT.

## 1.6 — Minecraft 1.20.1 Fabric

- Remote Access Tablets now load storage chunks asynchronously instead of freezing the server tick.
- Added temporary, non-persistent chunk leases that exist only while a remote request or terminal is open.
- Repeated tablet use now reuses the pending request instead of starting duplicate chunk loads.
- Added safe cancellation on disconnect, tablet switching, screen changes, timeouts and server shutdown.
- Remote network traversal never reads an unloaded chunk synchronously and is capped to prevent oversized networks from becoming chunk loaders.
- Newly linked tablets preload the Linker and Core chunks in parallel, while older tablets remain fully compatible.
- Recently closed remote sessions remain warm for 15 seconds, making quick reopenings immediate without permanent chunk loading.
- Optimized terminal pagination to copy only the 54 visible entries and cache sorted indexes for large storage systems.

## 1.5 — Minecraft 1.20.1 Fabric

- Added unique top textures for every Storage Core tier, the Storage Linker and the Crafting Terminal.
- Redesigned the Crafting Terminal as a futuristic 3×3 crafting matrix instead of reusing the vanilla crafting-table top.
- Added a wireless link-matrix design to the Storage Linker.
- Recolored the Tier 1 Storage Core with frosted white glass accents, making it clearly distinct from the diamond-cyan Tier 2 core.
- Kept a consistent dark chassis and circuit language across the complete TriStorage block set.

## 1.4 — Minecraft 1.20.1 Fabric

- Added the Crafting Terminal, combining the full TriStorage inventory with a vanilla 3×3 crafting grid.
- Crafting ingredients use real server-side stacks extracted from storage, preserving vanilla recipes,
  recipe remainders, advancements and item crafting callbacks.
- Crafting-grid contents return to storage when the screen closes; overflow safely returns to the
  player inventory or drops into the world instead of being deleted.
- Each player receives an independent crafting grid, preventing cross-player item access or races.
- Added a protected terminal-upgrade recipe that refuses to consume Storage Terminals carrying NBT.
- Blocked unsupported hotbar-swap and drag mutations on virtual storage display slots.
- Kept the vanilla-style interface, storage sorting, bulk deposit, pagination and compact counters.

## 1.3 — Minecraft 1.20.1 Fabric

This release includes all improvements introduced since 1.2.1:

### Inventory interaction

- Fixed Shift-click transfers previously blocked by the terminal's read-only display slots.
- Shift-click now moves full stacks reliably in both directions.
- Double-clicking a stored item withdraws all matching stacks that fit in the player inventory.
- Double-clicking a player item deposits all matching stacks, including the cursor stack.
- Storage mutations remain server-authoritative to prevent desynchronization and duplication.

### Vanilla-style interface

- Rebuilt the terminal around Minecraft's vanilla six-row chest interface.
- Added a compact vanilla-style control panel and smaller action buttons.
- Aligned the player inventory and hotbar with standard vanilla slot positions.
- Restored standard item tooltips for both the storage grid and player inventory.
- Added vanilla container background dimming.
- Restyled the Storage Core screen with vanilla colors, bevels and slot borders.

### Readability

- Reduced the size of stored-item quantity labels above 99 items so adjacent counters no longer overlap.
- Kept scaled quantity labels right-aligned to their own item slot.
- Added three-significant-digit compact quantities such as `1.29k`, `12.9k`,
  `129k`, with the same notation for millions and billions.
- Quantity labels now scale dynamically to remain inside their own slot at every magnitude.
- Rendered stored quantities at the correct effective Z=325, above the slot's Z=250
  item model and Z=300 decorations but below the Z=382 floating cursor item.
- Explicitly flushed the quantity text buffer before Minecraft renders the cursor stack.
- Removed cursor-intersection suppression, so moving an item no longer hides counters
  belonging to nearby storage slots.
- Tooltips now always render above stored quantities without text bleeding through their background.
- Removed the page-number shadow for sharper contrast on the vanilla panel.

## 1.2.2 — Minecraft 1.20.1 Fabric

- Rebuilt the terminal around Minecraft's vanilla six-row chest texture.
- Replaced the oversized dark control area with a compact vanilla-style side panel.
- Reduced the size of deposit, sort and page-navigation buttons.
- Restored standard vanilla item tooltips for the player inventory and storage grid.
- Added the vanilla background dimming used by normal container screens.
- Restyled the Storage Core screen with vanilla colors, bevels and slot borders.

## 1.2.1 — Minecraft 1.20.1 Fabric

- Fixed Shift-click transfers being blocked by the read-only terminal slots.
- Shift-click now explicitly moves one full stack from storage to the player,
  or the selected player stack into storage.
- Double-clicking a stored item now withdraws all matching stacks that fit in
  the player inventory.
- Double-clicking a player item now deposits all matching stacks, including the
  cursor stack, while respecting storage capacity.
- Kept all virtual-slot mutations authoritative on the server to prevent
  client desynchronization and item duplication.

## 1.2 — Minecraft 1.20.1 Fabric

- Storage cores now retain installed chests and stored items when mined.
- Filled cores are single-stack items and expose an offline-content tooltip.
- Core upgrade recipes preserve the portable storage payload across tiers.
- Terminals remain offline while their core is absent; placing the core restores access.
- Added **Deposit all** for the 36 player inventory and hotbar slots.
- Added registry-id, quantity and recent-item sort modes.
- Added shift-click, mouse-wheel, double-click fill and drop-key terminal actions.
- Synced terminal totals and per-type counts as full 64-bit values.
- Hardened item-variant merging and portable NBT sanitization.
- Prevented recursive core-within-core storage payloads.
