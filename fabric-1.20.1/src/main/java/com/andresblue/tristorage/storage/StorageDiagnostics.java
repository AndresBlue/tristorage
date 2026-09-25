package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.TriStorageMod;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.command.argument.BlockPosArgumentType;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

/** Small admin surface for baselines and production profiling. */
public final class StorageDiagnostics {
    private static final int BENCHMARK_MAX_TYPES = 100_000;
    private static final int BENCHMARK_TYPES_PER_TICK = 32;
    private static final Map<StorageId, BenchmarkTask> ACTIVE_BENCHMARKS = new HashMap<>();
    private static boolean initialized;

    private StorageDiagnostics() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerLifecycleEvents.SERVER_STOPPING.register(ignored -> ACTIVE_BENCHMARKS.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("tristorage")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(literal("metrics")
                            .executes(context -> showMetrics(
                                    context.getSource().getServer(), context.getSource()))
                            .then(literal("reset").executes(context -> {
                                StorageMetrics.reset();
                                context.getSource().sendFeedback(
                                        () -> Text.literal("TriStorage metrics reset."), false);
                                return 1;
                            }))
                            .then(literal("enable").executes(context -> {
                                StorageMetrics.setEnabled(true);
                                context.getSource().sendFeedback(
                                        () -> Text.literal("TriStorage metrics enabled."), false);
                                return 1;
                            }))
                            .then(literal("disable").executes(context -> {
                                StorageMetrics.setEnabled(false);
                                context.getSource().sendFeedback(
                                        () -> Text.literal("TriStorage metrics disabled."), false);
                                return 1;
                            }))
                            .then(literal("export").executes(context -> exportMetrics(
                                    context.getSource().getServer(), context.getSource()))))
                    .then(literal("inspect")
                            .then(argument("pos", BlockPosArgumentType.blockPos())
                                    .executes(context -> inspect(context.getSource(),
                                            BlockPosArgumentType.getLoadedBlockPos(
                                                    context, "pos")))))
                    .then(literal("selftest").executes(context ->
                            runSelfTest(context.getSource())))
                    .then(literal("benchmark")
                            .then(literal("generate")
                                    .then(argument("pos", BlockPosArgumentType.blockPos())
                                            .then(argument("types", IntegerArgumentType.integer(
                                                    1, BENCHMARK_MAX_TYPES))
                                                    .executes(context -> startBenchmark(
                                                            context.getSource(),
                                                            BlockPosArgumentType.getLoadedBlockPos(
                                                                    context, "pos"),
                                                            IntegerArgumentType.getInteger(
                                                                    context, "types"),
                                                            "mixed", new Random().nextLong()))
                                                    .then(argument("profile",
                                                            StringArgumentType.word())
                                                            .executes(context -> startBenchmark(
                                                                    context.getSource(),
                                                                    BlockPosArgumentType
                                                                            .getLoadedBlockPos(
                                                                                    context, "pos"),
                                                                    IntegerArgumentType.getInteger(
                                                                            context, "types"),
                                                                    StringArgumentType.getString(
                                                                            context, "profile"),
                                                                    0x54524953544f5241L))
                                                            .then(argument("seed",
                                                                    LongArgumentType.longArg())
                                                                    .executes(context ->
                                                                            startBenchmark(
                                                                                    context.getSource(),
                                                                                    BlockPosArgumentType
                                                                                            .getLoadedBlockPos(
                                                                                                    context,
                                                                                                    "pos"),
                                                                                    IntegerArgumentType
                                                                                            .getInteger(
                                                                                                    context,
                                                                                                    "types"),
                                                                                    StringArgumentType
                                                                                            .getString(
                                                                                                    context,
                                                                                                    "profile"),
                                                                                    LongArgumentType
                                                                                            .getLong(
                                                                                                    context,
                                                                                                    "seed"))))))))
                            .then(literal("status").executes(context ->
                                    benchmarkStatus(context.getSource()))))
                    .then(literal("storages")
                            .then(literal("list").executes(context -> listStorages(
                                    context.getSource())))
                            .then(literal("inspect")
                                    .then(argument("id", StringArgumentType.word())
                                            .executes(context -> inspectStorage(
                                                    context.getSource(),
                                                    StringArgumentType.getString(context, "id")))))
                            .then(literal("recover")
                                    .then(argument("id", StringArgumentType.word())
                                            .then(literal("confirm").executes(context ->
                                                    recoverStorage(context.getSource(),
                                                            StringArgumentType.getString(
                                                                    context, "id"))))))
                            .then(literal("purge")
                                    .then(argument("id", StringArgumentType.word())
                                            .then(literal("confirm").executes(context ->
                                                    purgeStorage(context.getSource(),
                                                            StringArgumentType.getString(
                                                                    context, "id"))))))));
        });
    }

    private static int startBenchmark(net.minecraft.server.command.ServerCommandSource source,
                                      BlockPos pos, int requestedTypes,
                                      String rawProfile, long seed) {
        BenchmarkProfile profile = BenchmarkProfile.parse(rawProfile);
        if (profile == null) {
            source.sendError(Text.literal("Unknown benchmark profile '" + rawProfile
                    + "'. Use mixed, nbt, heavy or bulk."));
            return 0;
        }
        if (!(source.getWorld().getBlockEntity(pos) instanceof StorageCoreBlockEntity core)) {
            source.sendError(Text.literal("No Storage Core at " + pos.toShortString()));
            return 0;
        }
        StorageRuntime runtime = core.runtime();
        if (!runtime.isReady() || core.isRecoveryRequired()) {
            source.sendError(Text.literal("The Storage Core is still loading or requires recovery."));
            return 0;
        }
        if (core.installedChests() <= 0) {
            source.sendError(Text.literal("The Storage Core needs at least one installed chest."));
            return 0;
        }
        int availableTypes = Math.max(0, core.typeCapacity() - core.storedTypes());
        if (availableTypes <= 0 || core.itemCapacity() <= core.totalItems()) {
            source.sendError(Text.literal("The Storage Core has no free capacity for benchmark data."));
            return 0;
        }
        if (ACTIVE_BENCHMARKS.containsKey(runtime.id())) {
            source.sendError(Text.literal("A benchmark is already running for this storage."));
            return 0;
        }
        List<Item> candidates = benchmarkCandidates(seed);
        BenchmarkTask task = new BenchmarkTask(source, core, requestedTypes, profile, seed,
                candidates);
        ACTIVE_BENCHMARKS.put(runtime.id(), task);
        StorageMetrics.add("benchmark.types_requested", requestedTypes);
        StorageMetrics.add("benchmark.items_requested", task.requestedItems());
        StorageMetrics.increment("benchmark.started");
        StorageTickCoordinator.schedule(task);
        source.sendFeedback(() -> Text.literal("Benchmark generation started: "
                + requestedTypes + " types, profile=" + profile.id
                + ", seed=" + seed + ". It runs in bounded batches; use "
                + "/tristorage benchmark status to monitor it. Available Core capacity: "
                + availableTypes + " types / "
                + Math.max(0, core.itemCapacity() - core.totalItems()) + " items."), false);
        return 1;
    }

    private static int benchmarkStatus(net.minecraft.server.command.ServerCommandSource source) {
        if (ACTIVE_BENCHMARKS.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No TriStorage benchmark is running."), false);
            return 0;
        }
        ACTIVE_BENCHMARKS.values().forEach(task -> source.sendFeedback(
                () -> Text.literal(task.status()), false));
        return ACTIVE_BENCHMARKS.size();
    }

    private static List<Item> benchmarkCandidates(long seed) {
        List<Item> result = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds()) {
            if (TriStorageMod.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            Item item = Registries.ITEM.get(id);
            if (item != null && item != Items.AIR && item.getMaxCount() > 0) {
                result.add(item);
            }
        }
        result.sort(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()));
        Collections.shuffle(result, new Random(seed));
        return List.copyOf(result);
    }

    private enum BenchmarkProfile {
        MIXED("mixed"),
        NBT("nbt"),
        HEAVY("heavy"),
        BULK("bulk");

        private final String id;

        BenchmarkProfile(String id) {
            this.id = id;
        }

        private static BenchmarkProfile parse(String value) {
            for (BenchmarkProfile profile : values()) {
                if (profile.id.equalsIgnoreCase(value)) {
                    return profile;
                }
            }
            return null;
        }
    }

    private static final class BenchmarkTask implements StorageTickCoordinator.BoundedTask {
        private final net.minecraft.server.command.ServerCommandSource source;
        private final StorageCoreBlockEntity core;
        private final StorageId storageId;
        private final int requestedTypes;
        private final BenchmarkProfile profile;
        private final long seed;
        private final List<Item> candidates;
        private final long requestedItems;
        private int processedTypes;
        private long insertedItems;
        private boolean complete;
        private boolean capacityReached;

        private BenchmarkTask(net.minecraft.server.command.ServerCommandSource source,
                              StorageCoreBlockEntity core, int requestedTypes,
                              BenchmarkProfile profile, long seed, List<Item> candidates) {
            this.source = source;
            this.core = core;
            this.storageId = core.storageId();
            this.requestedTypes = requestedTypes;
            this.profile = profile;
            this.seed = seed;
            this.candidates = candidates;
            this.requestedItems = saturatedMultiply(requestedTypes, countFor(profile));
        }

        private long requestedItems() {
            return requestedItems;
        }

        @Override
        public boolean complete() {
            return complete;
        }

        @Override
        public int step(int budget) {
            if (complete || core.isRemoved() || !(core.getWorld() instanceof net.minecraft.server.world.ServerWorld)
                    || !core.storageId().equals(storageId)) {
                finish(false);
                return 0;
            }
            int work = Math.min(Math.min(Math.max(1, budget), BENCHMARK_TYPES_PER_TICK),
                    requestedTypes - processedTypes);
            int consumed = 0;
            while (consumed < work && processedTypes < requestedTypes) {
                int index = processedTypes++;
                ItemStack template = templateFor(index);
                long requested = countFor(profile);
                long accepted = core.insert(template, requested);
                insertedItems = saturatedAdd(insertedItems, accepted);
                consumed++;
                StorageMetrics.increment("benchmark.types_processed");
                StorageMetrics.add("benchmark.items_inserted", accepted);
                if (accepted < requested) {
                    capacityReached = true;
                    complete = true;
                    break;
                }
            }
            if (processedTypes >= requestedTypes) {
                complete = true;
            }
            if (complete) {
                finish(true);
            }
            return consumed;
        }

        private ItemStack templateFor(int index) {
            return switch (profile) {
                case MIXED -> index < candidates.size()
                        ? new ItemStack(candidates.get(index))
                        : variantStack(Items.PAPER, profile, index, seed);
                case NBT -> variantStack(Items.PAPER, profile, index, seed);
                case HEAVY -> heavyStack(index, seed);
                case BULK -> index == 0
                        ? new ItemStack(Items.STONE)
                        : variantStack(Items.STONE, profile, index, seed);
            };
        }

        private void finish(boolean generated) {
            if (!ACTIVE_BENCHMARKS.remove(storageId, this)) {
                complete = true;
                return;
            }
            complete = true;
            if (generated) {
                StorageMetrics.increment("benchmark.completed");
            } else {
                StorageMetrics.increment("benchmark.aborted");
            }
            String suffix = capacityReached ? " Capacity reached." : "";
            source.sendFeedback(() -> Text.literal("Benchmark generation "
                    + (generated ? "finished" : "stopped") + ": " + processedTypes
                    + "/" + requestedTypes + " types, " + insertedItems
                    + " items inserted." + suffix), false);
        }

        private String status() {
            return "Benchmark " + storageId + ": " + processedTypes + "/"
                    + requestedTypes + " types, " + insertedItems + " items inserted, profile="
                    + profile.id + ", seed=" + seed;
        }

        private static long countFor(BenchmarkProfile profile) {
            return switch (profile) {
                case MIXED, NBT -> 64L;
                case HEAVY -> 16L;
                case BULK -> 1_000_000L;
            };
        }

        private static ItemStack variantStack(Item item, BenchmarkProfile profile,
                                              int index, long seed) {
            ItemStack stack = new ItemStack(item);
            NbtCompound tag = stack.getOrCreateNbt();
            tag.putString("TriStorageBenchmarkProfile", profile.id);
            tag.putInt("TriStorageBenchmarkVariant", index);
            tag.putLong("TriStorageBenchmarkSeed", seed);
            return stack;
        }

        private static ItemStack heavyStack(int index, long seed) {
            ItemStack stack = variantStack(Items.SHULKER_BOX, BenchmarkProfile.HEAVY,
                    index, seed);
            NbtCompound blockEntityTag = new NbtCompound();
            NbtList contents = new NbtList();
            for (int slot = 0; slot < 27; slot++) {
                NbtCompound entry = new NbtCompound();
                entry.putByte("Slot", (byte) slot);
                entry.putString("id", slot % 3 == 0
                        ? "minecraft:iron_ingot" : "minecraft:stone");
                entry.putByte("Count", (byte) (1 + slot % 4));
                contents.add(entry);
            }
            blockEntityTag.put("Items", contents);
            blockEntityTag.putInt("TriStorageBenchmarkPayload", index);
            stack.getOrCreateNbt().put("BlockEntityTag", blockEntityTag);
            return stack;
        }

        private static long saturatedAdd(long value, long delta) {
            if (delta > 0 && value > Long.MAX_VALUE - delta) {
                return Long.MAX_VALUE;
            }
            return value + delta;
        }

        private static long saturatedMultiply(long value, long factor) {
            if (value > 0 && factor > 0 && value > Long.MAX_VALUE / factor) {
                return Long.MAX_VALUE;
            }
            return value * factor;
        }
    }

    private static int runSelfTest(net.minecraft.server.command.ServerCommandSource source) {
        try {
            ItemStack tagged = new ItemStack(Items.DIAMOND_SWORD);
            tagged.getOrCreateNbt().putString("TriStorageTest", "same");
            ItemStack same = tagged.copy();
            ItemStack different = tagged.copy();
            different.getOrCreateNbt().putString("TriStorageTest", "different");
            if (ItemKey.frozen(tagged).equals(ItemKey.probe(same))
                    != ItemStack.canCombine(tagged, same)
                    || ItemKey.frozen(tagged).equals(ItemKey.probe(different))
                    != ItemStack.canCombine(tagged, different)) {
                throw new IllegalStateException("ItemKey/canCombine mismatch");
            }

            StorageRuntime runtime = new StorageRuntime(StorageId.random());
            for (int index = 0; index < 100; index++) {
                if (runtime.insert(new ItemStack(Items.STONE), 1,
                        1_000, 1_000_000) != 1) {
                    throw new IllegalStateException("insert failed at " + index);
                }
            }
            if (runtime.revision() != 0) {
                throw new IllegalStateException("revision changed before consolidation");
            }
            runtime.flushChanges();
            if (runtime.revision() != 1 || runtime.totalItems() != 100) {
                throw new IllegalStateException("100 mutations did not produce one revision");
            }
            StorageRuntime.BrowseResult page = runtime.browse(0, 54,
                    StorageRuntime.EntryOrder.REGISTRY,
                    TerminalFilter.sanitize("", TerminalFilter.CategoryMode.NONE,
                            TerminalFilter.ALL));
            if (page.entries().size() > 54 || page.entries().isEmpty()) {
                throw new IllegalStateException("invalid page size");
            }
            long removedId = page.entries().get(0).id();
            runtime.extract(removedId, 64);
            runtime.extract(removedId, 64);
            runtime.insert(new ItemStack(Items.DIRT), 1, 1_000, 1_000_000);
            if (!runtime.extract(removedId, 64).isEmpty()) {
                throw new IllegalStateException("stale EntryId targeted a replacement");
            }
            source.sendFeedback(() -> Text.literal(
                    "TriStorage storage self-test passed."), false);
            return 1;
        } catch (RuntimeException failure) {
            source.sendError(Text.literal(
                    "TriStorage storage self-test failed: " + failure.getMessage()));
            return 0;
        }
    }

    private static int listStorages(net.minecraft.server.command.ServerCommandSource source) {
        var storages = StorageRepositories.get(source.getServer()).listStorages();
        source.sendFeedback(() -> Text.literal(
                "TriStorage repository: " + storages.size() + " storages"), false);
        storages.stream().limit(50).forEach(storage -> source.sendFeedback(
                () -> Text.literal(storage.id() + " " + storage.lifecycle()
                        + (storage.ready() ? " " + storage.storedTypes() + " types / "
                        + storage.storedItems() + " items" : " (not loaded)")), false));
        if (storages.size() > 50) {
            source.sendFeedback(() -> Text.literal(
                    "Showing the first 50 storages."), false);
        }
        return storages.size();
    }

    private static int inspectStorage(net.minecraft.server.command.ServerCommandSource source,
                                      String rawId) {
        StorageId id = parseId(source, rawId);
        if (id == null) {
            return 0;
        }
        StorageRepository.StorageSummary storage = StorageRepositories
                .get(source.getServer()).inspect(id);
        source.sendFeedback(() -> Text.literal(storage.id() + ": "
                + storage.lifecycle() + ", ready=" + storage.ready()
                + ", anchors=" + storage.anchors()
                + ", chests=" + storage.installedChests()
                + ", types=" + storage.storedTypes()
                + ", items=" + storage.storedItems()), false);
        if (!storage.ready() && !"MISSING".equals(storage.lifecycle())) {
            source.sendFeedback(() -> Text.literal(
                    "The storage is loading; run the command again shortly."), false);
        }
        return "MISSING".equals(storage.lifecycle()) ? 0 : 1;
    }

    private static int recoverStorage(net.minecraft.server.command.ServerCommandSource source,
                                      String rawId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        StorageId id = parseId(source, rawId);
        if (id == null) {
            return 0;
        }
        ServerPlayerEntity player = source.getPlayer();
        StorageRepository.PortableRecovery recovery = StorageRepositories
                .get(source.getServer()).recoverPortable(id);
        if (!"RECOVERED".equals(recovery.result())) {
            source.sendError(Text.literal("Storage could not be recovered: "
                    + recovery.result() + ". Inspect it and retry after loading."));
            return 0;
        }
        StorageRepository.StorageSummary summary = recovery.summary();
        ItemStack portable = new ItemStack(switch (StorageTier.forRecovery(
                recovery.tier(), summary.installedChests())) {
            case IRON -> TriStorageMod.IRON_CORE;
            case DIAMOND -> TriStorageMod.DIAMOND_CORE;
            case BLAZE -> TriStorageMod.BLAZE_CORE;
            case COSMIC -> TriStorageMod.COSMIC_CORE;
        });
        NbtCompound data = new NbtCompound();
        data.putString(PortableCoreData.STORAGE_ID_KEY, id.toString());
        data.putString(PortableCoreData.OWNERSHIP_TOKEN_KEY, recovery.token());
        data.putInt(PortableCoreData.FORMAT_VERSION_KEY, 1);
        data.putInt(PortableCoreData.CHESTS_KEY, summary.installedChests());
        data.putInt(PortableCoreData.TYPES_SUMMARY_KEY, summary.storedTypes());
        data.putLong(PortableCoreData.ITEMS_SUMMARY_KEY, summary.storedItems());
        data.put(PortableCoreData.ENTRIES_KEY, new NbtList());
        PortableCoreData.applyTo(portable, data);
        if (!player.getInventory().insertStack(portable)) {
            player.dropItem(portable, false);
        }
        source.sendFeedback(() -> Text.literal(
                "Recovered " + id + " as one portable Core."), true);
        return 1;
    }

    private static int purgeStorage(net.minecraft.server.command.ServerCommandSource source,
                                    String rawId) {
        StorageId id = parseId(source, rawId);
        if (id == null) {
            return 0;
        }
        String result = StorageRepositories.get(source.getServer()).purgeEmpty(id);
        if (!"PURGED".equals(result)) {
            source.sendError(Text.literal("Storage was not purged: " + result
                    + ". Only fully loaded, unused storages with zero chests and items qualify."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Purged empty storage " + id + "."), true);
        return 1;
    }

    private static StorageId parseId(net.minecraft.server.command.ServerCommandSource source,
                                     String rawId) {
        try {
            return StorageId.parse(rawId);
        } catch (IllegalArgumentException invalid) {
            source.sendError(Text.literal("Invalid storage UUID: " + rawId));
            return null;
        }
    }

    private static int showMetrics(MinecraftServer server,
                                   net.minecraft.server.command.ServerCommandSource source) {
        StorageRepository.Diagnostics repository =
                StorageRepositories.get(server).diagnostics();
        source.sendFeedback(() -> Text.literal("TriStorage: "
                + (StorageMetrics.enabled() ? "metrics ON, " : "metrics OFF, ")
                + repository.loadedRuntimes() + " runtimes, "
                + repository.loadingRuntimes() + " loading, "
                + repository.storedTypes() + " types, "
                + repository.estimatedBytes() / (1024 * 1024) + " MiB estimated"), false);
        StorageMetrics.snapshot().forEach((key, value) ->
                source.sendFeedback(() -> Text.literal(key + " = " + value), false));
        return 1;
    }

    private static int exportMetrics(MinecraftServer server,
                                     net.minecraft.server.command.ServerCommandSource source) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timestamp", Instant.now().toString());
        report.put("repository", StorageRepositories.get(server).diagnostics());
        report.put("counters", StorageMetrics.snapshot());
        Path output = server.getSavePath(WorldSavePath.ROOT).resolve("data")
                .resolve("tristorage").resolve("metrics.json");
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create()
                    .toJson(report));
            source.sendFeedback(() -> Text.literal(
                    "TriStorage metrics exported to " + output), false);
            return 1;
        } catch (Exception exception) {
            source.sendError(Text.literal("Could not export TriStorage metrics: "
                    + exception.getMessage()));
            return 0;
        }
    }

    private static int inspect(net.minecraft.server.command.ServerCommandSource source,
                               net.minecraft.util.math.BlockPos pos) {
        if (!(source.getWorld().getBlockEntity(pos) instanceof StorageCoreBlockEntity core)) {
            source.sendError(Text.literal("No Storage Core at " + pos.toShortString()));
            return 0;
        }
        StorageRuntime runtime = core.runtime();
        source.sendFeedback(() -> Text.literal("Storage " + runtime.id()
                + ": ready=" + runtime.isReady()
                + ", chests=" + runtime.installedChests()
                + ", types=" + runtime.storedTypes()
                + ", items=" + runtime.totalItems()), false);
        return 1;
    }
}
