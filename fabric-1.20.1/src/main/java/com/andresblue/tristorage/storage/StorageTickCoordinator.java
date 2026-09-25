package com.andresblue.tristorage.storage;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.Collections;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Set;

/** Coalesces notifications from all operations touching a storage in one tick. */
public final class StorageTickCoordinator {
    private static final Set<StorageRuntime> DIRTY =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ArrayDeque<BoundedTask> BOUNDED_TASKS = new ArrayDeque<>();
    private static final int BACKGROUND_BUDGET = 256;
    private static boolean initialized;

    private StorageTickCoordinator() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            runBoundedTasks();
            flush();
        });
    }

    static void markDirty(StorageRuntime runtime) {
        DIRTY.add(runtime);
    }

    public static void schedule(BoundedTask task) {
        if (task != null && !BOUNDED_TASKS.contains(task)) {
            BOUNDED_TASKS.add(task);
        }
    }

    private static void runBoundedTasks() {
        int budget = BACKGROUND_BUDGET;
        int turns = BOUNDED_TASKS.size();
        while (budget > 0 && turns-- > 0 && !BOUNDED_TASKS.isEmpty()) {
            BoundedTask task = BOUNDED_TASKS.remove();
            int consumed = Math.max(0, Math.min(budget, task.step(budget)));
            budget -= consumed;
            if (!task.complete()) {
                BOUNDED_TASKS.add(task);
            }
        }
    }

    public static void flush() {
        if (DIRTY.isEmpty()) {
            return;
        }
        StorageRuntime[] runtimes = DIRTY.toArray(StorageRuntime[]::new);
        DIRTY.clear();
        for (StorageRuntime runtime : runtimes) {
            runtime.flushChanges();
        }
    }

    public interface BoundedTask {
        int step(int budget);

        boolean complete();
    }
}
