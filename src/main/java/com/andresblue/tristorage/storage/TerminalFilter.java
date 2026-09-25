package com.andresblue.tristorage.storage;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class TerminalFilter {
    public static final String ALL = "all";
    public static final String UNCATEGORIZED = "tristorage:uncategorized";
    public static final int MAX_QUERY_LENGTH = 64;
    public static final int MAX_CATEGORY_LENGTH = 96;
    private static int creativeGeneration;
    private static Map<Item, List<String>> creativeCategoriesByItem = Map.of();
    private static List<String> orderedCreativeCategories = List.of();
    private static Object preparedRegistryManager;
    private static Object preparedEnabledFeatures;
    private static boolean creativeContextPrepared;

    private TerminalFilter() {
    }

    public static Selection sanitize(String query, CategoryMode mode, String category) {
        String safeQuery = normalize(limit(query, MAX_QUERY_LENGTH));
        CategoryMode safeMode = mode == null ? CategoryMode.NONE : mode;
        String safeCategory = normalize(limit(category, MAX_CATEGORY_LENGTH));
        if (safeCategory.isBlank() || safeMode == CategoryMode.NONE) {
            safeCategory = ALL;
        }
        return new Selection(safeQuery, safeMode, safeCategory);
    }

    public static String searchText(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String modName = FabricLoader.getInstance().getModContainer(id.getNamespace())
                .map(container -> container.getMetadata().getName())
                .orElse(id.getNamespace());
        return normalize(String.join(" ",
                id.toString(),
                id.getNamespace(),
                modName,
                id.getPath().replace('_', ' '),
                stack.getDescriptionId(),
                stack.getHoverName().getString()
        ));
    }

    public static String modCategory(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    public static void prepareCreativeGroups(ServerPlayer player) {
        prepareCreativeGroups(player.serverLevel());
    }

    /**
     * ItemGroups.updateDisplayContext is extremely expensive in large packs.
     * The registry manager and enabled feature set are server-wide immutable
     * context for a normal play session, so repeating it for the remote request,
     * screen handler and every reopen only creates visible stalls.
     */
    public static void prepareCreativeGroups(net.minecraft.server.level.ServerLevel world) {
        Object registryManager = world.registryAccess();
        Object enabledFeatures = world.enabledFeatures();
        if (creativeContextPrepared
                && preparedRegistryManager == registryManager
                && Objects.equals(preparedEnabledFeatures, enabledFeatures)) {
            StorageMetrics.increment("creative_groups.context_cache_hits");
            return;
        }
        long started = StorageMetrics.startTimer();
        boolean contextChanged = CreativeModeTabs.tryRebuildTabContents(
                world.enabledFeatures(), true, world.registryAccess());
        if (contextChanged || creativeCategoriesByItem.isEmpty()) {
            rebuildCreativeCategoryIndex();
        }
        preparedRegistryManager = registryManager;
        preparedEnabledFeatures = enabledFeatures;
        creativeContextPrepared = true;
        StorageMetrics.stopTimer("creative_groups.prepare", started);
    }

    /** Resource/data-pack reloads may alter tags and creative tab contents. */
    public static void invalidateCreativeGroups() {
        creativeContextPrepared = false;
        preparedRegistryManager = null;
        preparedEnabledFeatures = null;
    }

    public static int creativeGeneration() {
        return creativeGeneration;
    }

    public static List<String> creativeCategories(ItemStack stack) {
        return creativeCategoriesByItem.getOrDefault(stack.getItem(), List.of());
    }

    private static void rebuildCreativeCategoryIndex() {
        Map<Item, LinkedHashSet<String>> mutableIndex = new IdentityHashMap<>();
        List<String> ordered = new ArrayList<>();
        for (CreativeModeTab group : CreativeModeTabs.tabs()) {
            if (group.getType() != CreativeModeTab.Type.CATEGORY || !group.hasAnyItems()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
            if (id == null) {
                continue;
            }
            String category = id.toString();
            ordered.add(category);
            for (ItemStack displayed : group.getDisplayItems()) {
                mutableIndex.computeIfAbsent(displayed.getItem(), ignored -> new LinkedHashSet<>())
                        .add(category);
            }
        }
        Map<Item, List<String>> immutableIndex = new IdentityHashMap<>();
        mutableIndex.forEach((item, categories) ->
                immutableIndex.put(item, List.copyOf(categories)));
        creativeCategoriesByItem = immutableIndex;
        orderedCreativeCategories = List.copyOf(ordered);
        creativeGeneration++;
    }

    public static List<String> orderedCreativeCategories() {
        return orderedCreativeCategories;
    }

    public static boolean matchesQuery(String indexedText, String normalizedQuery) {
        return matchesQuery(indexedText, queryTokens(normalizedQuery));
    }

    public static List<String> queryTokens(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return List.of();
        }
        if (normalizedQuery.indexOf(' ') < 0) {
            return List.of(normalizedQuery);
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalizedQuery.split("\\s+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    public static boolean matchesQuery(String indexedText, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return true;
        }
        for (String token : tokens) {
            if (!indexedText.contains(token)) {
                return false;
            }
        }
        return true;
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).trim();
    }

    private static String limit(String value, int maximum) {
        if (value == null) {
            return "";
        }
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    public enum CategoryMode {
        NONE,
        TYPE,
        MOD;

        public static CategoryMode byNetworkId(int id) {
            return id >= 0 && id < values().length ? values()[id] : NONE;
        }
    }

    public record Selection(String query, CategoryMode mode, String category) {
    }
}
