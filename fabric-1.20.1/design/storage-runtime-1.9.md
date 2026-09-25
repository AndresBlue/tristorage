# TriStorage 1.9 storage runtime

## Invariants

- StorageRepository is the only persistent owner of a migrated storage.
- StorageRuntime is the only loaded authoritative item state.
- StorageCoreBlockEntity is an anchor containing identity, ownership token
  and summaries; it is not the catalog file.
- All world, inventory and networking mutations stay on the server thread.
- File IO and NBT frame recovery run on the dedicated storage IO worker.
- A stored type is identified by item identity plus immutable NBT, matching
  ItemStack.canCombine.
- Entry IDs are monotonic and never reused.
- A normal terminal page contains no more than 54 entries.
- Non-empty storages are never automatically deleted.

## Files

~~~text
world/data/tristorage/
  manifest.nbt
  storages/
    <uuid>.snapshot.nbt
    <uuid>.journal
    <uuid>.journal.sealed
  temp/
~~~

Snapshot commits use fsync plus atomic rename. Journal frames contain a magic,
format version, payload length and CRC32. Recovery truncates an incomplete or
invalid tail. Operations store the final state for each changed EntryId, so
replay is idempotent.

At 16 MiB the active journal is sealed, a replacement journal becomes
available, and the worker compacts snapshot plus sealed frames. A crash before
or after the snapshot rename remains recoverable.

## Runtime lifecycle

Disk reads occur on the IO worker. Prepared NBT entries are published on the
server thread at a maximum of 256 types per tick. Legacy BlockEntity entries
also convert to ItemStack/ItemKey form through the bounded scheduler; their
original NBT remains authoritative until the first snapshot is durable.
Remote opens remain pending until publication completes.

The default runtime cache budget is 10% of the JVM maximum heap, clamped to
64–512 MiB. An unanchored runtime must be idle for five minutes before LRU
eviction. Eviction waits for and forces its journal before releasing memory;
it never removes persistent storage.

## Mutation and views

Every mutation updates hash lookup and ordered sets immediately. Dirty
storages are consolidated at END_SERVER_TICK; any number of operations in
that tick produce one content revision.

Equivalent terminal filters share one view. Count changes reposition one entry
and adjust totals by delta. Structural changes evaluate only the new/removed
entry against active views. Sequential page navigation uses stable entry
cursors, avoiding traversal of previous pages. Identical hot pages share one
immutable entry snapshot; each handler creates only its own display stacks.

Virtual-slot actions carry StorageId, EntryId and the page content revision.
The server validates the open handler and remote lease before applying them.
A stale action can only request a resync, never target the item that later
moved into the same visual slot.

## Portable ownership

The portable item stores TriStorageId, OwnershipToken, format version,
installed chests and display summaries. Breaking a Core flushes pending
changes and requests a non-blocking IO barrier. If a recent mutation is still
being forced, the first break is cancelled and can be retried without freezing
the server tick. A confirmed break rotates the token and commits the ACTIVE to
PORTABLE transition. Placement validates and rotates it again.
Cloned or stale tokens enter a non-destructive recovery-required state.

Operator commands can list and inspect repository records. Recovery requires
an explicit `confirm` and yields exactly one token-bearing portable Core;
purge also requires confirmation and only accepts unanchored storages with
zero items and zero installed chests.
