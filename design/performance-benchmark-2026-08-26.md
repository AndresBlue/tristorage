# TriStorage 1.10 performance benchmark — 2026-08-26

## Scope

Deterministic backend stress matrix for Fabric 1.20.1. Timings are descriptive;
correctness and structural work counters are the pass/fail criteria so CI does
not depend on a particular CPU.

Environment used for this run:

- Java runtime: GraalVM 21.0.12 (compiled for Java 17)
- Available processors: 24
- Test heap: 2 GiB
- Three complete post-change executions

## Scenarios

- 1.000, 10.000, 25.000, 50.000 and 100.000 NBT-distinct item types.
- Registry/count/deep-page navigation and cold/warm text filters.
- Eight simulated handlers sharing one filtered view while performing 2.000
  mutations over 250 server-tick batches.
- 100.000 deterministic random insert/extract operations over 25.000 types,
  checked entry-by-entry against an independent count model.
- 10.000 heavy shulker-like NBT variants.
- 2.000 CRC-framed journal writes followed by full recovery.

## Results

| Measurement | Run 1 | Run 2 | Run 3 |
| --- | ---: | ---: | ---: |
| Eight actors p50 | 0.800 ms | 0.650 ms | 0.510 ms |
| Eight actors p95 | 0.930 ms | 0.870 ms | 0.910 ms |
| Eight actors p99 | 1.010 ms | 0.950 ms | 1.080 ms |
| Eight actors worst batch | 1.370 ms | 1.290 ms | 1.300 ms |
| First 100k search | 109.71 ms | 110.50 ms | 83.37 ms |
| 100k randomized operations | 377.57 ms | 350.02 ms | 330.64 ms |

The pre-change first-search baseline at 100.000 types was 239.70 ms. Search
metadata precomputation reduced it by roughly 54–65%. A first uncached filter
still necessarily visits its candidates once; subsequent identical filters are
shared and complete in approximately 0.03–0.12 ms in these runs.

All hot multiplayer runs produced:

- one consolidated revision per simulated server tick;
- zero full scans and zero full sorts;
- one shared page snapshot for equivalent viewers;
- no incorrect amount, merge, loss or duplication in the random model.

## Remote-open changes

- Linker and known Core chunks are requested in parallel on a cold open.
- Recent remote sessions remain warm for five minutes instead of 30 seconds.
- Idle warm leases are globally capped at 16 chunks and evicted least-recently
  used, so this does not become permanent chunk loading.
- Journal ItemStack/NBT serialization runs on the repository IO worker.
- Storage mutations request one normal end-of-tick terminal refresh instead of
  refreshing immediately after every transfer.

## Interpretation

The synthetic matrix isolates TriStorage's runtime and persistence algorithms;
it does not reproduce another modpack's chunk-generation or world-IO cost. A
cold tablet open after the five-minute lease expires can still wait for
Minecraft to load a chunk, but that load remains asynchronous and no longer
reconstructs the complete storage catalog on the server thread.

For an integrated modpack run, enable metrics before the test and export them
afterwards:

```text
/tristorage metrics reset
/tristorage metrics enable
/tristorage metrics export
```

The resulting `world/data/tristorage/metrics.json` separates chunk load,
repository load, journal capture/serialization, page refresh and remote-open
latency.
