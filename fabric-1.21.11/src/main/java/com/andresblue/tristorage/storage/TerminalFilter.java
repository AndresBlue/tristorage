package com.andresblue.tristorage.storage;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-side search and creative-category index shared by every terminal. */
public final class TerminalFilter {
    public static final String ALL = "all";
    public static final String UNCATEGORIZED = "tristorage:uncategorized";
    public static final int MAX_QUERY_LENGTH = 64;
    public static final int MAX_CATEGORY_LENGTH = 96;

    private static int creativeGeneration;
    private static Map<Item, List<String>> creativeCategoriesByItem = Map.of();
    private static List<String> orderedCreativeCategories = List.of();

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
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String modName = FabricLoader.getInstance().getModContainer(id.getNamespace())
                .map(container -> container.getMetadata().getName())
                .orElse(id.getNamespace());
        return normalize(String.join(" ", id.toString(), id.getNamespace(), modName,
                id.getPath().replace('_', ' '), stack.getName().getString()));
    }

    public static String modCategory(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getNamespace();
    }

    public static void prepareCreativeGroups(ServerPlayerEntity player) {
        boolean contextChanged = ItemGroups.updateDisplayContext(
                player.getEntityWorld().getEnabledFeatures(), true,
                player.getEntityWorld().getRegistryManager());
        if (contextChanged || creativeCategoriesByItem.isEmpty()) {
            rebuildCreativeCategoryIndex();
        }
    }

    public static int creativeGeneration() {
        return creativeGeneration;
    }

    public static List<String> creativeCategories(ItemStack stack) {
        return creativeCategoriesByItem.getOrDefault(stack.getItem(), List.of());
    }

    public static List<String> orderedCreativeCategories() {
        return orderedCreativeCategories;
    }

    private static void rebuildCreativeCategoryIndex() {
        Map<Item, LinkedHashSet<String>> mutableIndex = new IdentityHashMap<>();
        List<String> ordered = new ArrayList<>();
        for (ItemGroup group : ItemGroups.getGroupsToDisplay()) {
            if (group.getType() != ItemGroup.Type.CATEGORY || !group.hasStacks()) {
                continue;
            }
            Identifier id = Registries.ITEM_GROUP.getId(group);
            if (id == null) {
                continue;
            }
            String category = id.toString();
            ordered.add(category);
            for (ItemStack displayed : group.getDisplayStacks()) {
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

    public static boolean matchesQuery(String indexedText, String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return true;
        }
        for (String token : normalizedQuery.split("\\s+")) {
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
