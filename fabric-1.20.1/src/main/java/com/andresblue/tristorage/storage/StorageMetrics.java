package com.andresblue.tristorage.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Opt-in counters used by the performance benchmark and diagnostics. */
public final class StorageMetrics {
    private static final Map<String, LongAdder> COUNTERS = new ConcurrentHashMap<>();
    private static volatile boolean enabled = Boolean.getBoolean("tristorage.metrics");

    private StorageMetrics() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        StorageMetrics.enabled = enabled;
    }

    public static void increment(String name) {
        add(name, 1);
    }

    public static void add(String name, long value) {
        if (enabled) {
            COUNTERS.computeIfAbsent(name, ignored -> new LongAdder()).add(value);
        }
    }

    public static long startTimer() {
        return enabled ? System.nanoTime() : 0L;
    }

    public static void stopTimer(String name, long startedAt) {
        if (enabled && startedAt != 0L) {
            add(name + ".nanos", System.nanoTime() - startedAt);
            increment(name + ".calls");
        }
    }

    public static Map<String, Long> snapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        COUNTERS.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), entry.getValue().sum()));
        return Map.copyOf(result);
    }

    public static void reset() {
        COUNTERS.clear();
    }
}
