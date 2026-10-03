package com.andresblue.tristorage.version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
//? if >=1.20 {
import java.util.LinkedHashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
//?} else {
/*import java.util.IdentityHashMap;
import java.util.Locale;
*///?}

/**
 * Creative tabs as item categories. Minecraft 1.20 made them a registry with
 * ids whose contents are built on demand, and an item can sit in several.
 * Before, they were a fixed array of named tabs and every item named its one.
 */
public final class CreativeTabs {
    private CreativeTabs() {
    }

    /** The tabs that sort items into categories: not search, hotbar or inventory. */
    public static List<CreativeModeTab> categoryTabs() {
        List<CreativeModeTab> tabs = new ArrayList<>();
        //? if >=1.20 {
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY) {
                tabs.add(tab);
            }
        }
        //?} else {
        /*for (CreativeModeTab tab : CreativeModeTab.TABS) {
            if (tab != CreativeModeTab.TAB_SEARCH && tab != CreativeModeTab.TAB_HOTBAR
                    && tab != CreativeModeTab.TAB_INVENTORY) {
                tabs.add(tab);
            }
        }
        *///?}
        return tabs;
    }

    /** The name client and server both know a tab by, or null when it has none. */
    @Nullable
    public static String id(CreativeModeTab tab) {
        //? if >=1.20 {
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        return id == null ? null : id.toString();
        //?} else {
        /*return tab.getRecipeFolderName().toLowerCase(Locale.ROOT);
        *///?}
    }

    @Nullable
    public static CreativeModeTab byId(String id) {
        //? if >=1.20 {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(key).orElse(null);
        //?} else {
        /*for (CreativeModeTab tab : CreativeModeTab.TABS) {
            if (id.equals(id(tab))) {
                return tab;
            }
        }
        return null;
        *///?}
    }

    /** The namespace of the mod that added a tab. */
    public static String namespace(CreativeModeTab tab) {
        //? if >=1.20 {
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        return id == null ? "minecraft" : id.getNamespace();
        //?} else {
        /*// Fabric names a mod's tab "namespace.path"; vanilla names have no dot.
        String name = tab.getRecipeFolderName();
        int dot = name.indexOf('.');
        return dot < 0 ? "minecraft" : name.substring(0, dot);
        *///?}
    }

    /**
     * The items of every category tab that has any, by tab id and in tab
     * order. From 1.20 on the tabs' contents must have been built first.
     */
    public static Map<String, Collection<Item>> itemsByCategory() {
        Map<String, Collection<Item>> categories = new LinkedHashMap<>();
        //? if >=1.20 {
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            String id = id(tab);
            if (id == null || tab.getType() != CreativeModeTab.Type.CATEGORY || !tab.hasAnyItems()) {
                continue;
            }
            Collection<Item> items = new LinkedHashSet<>();
            for (ItemStack displayed : tab.getDisplayItems()) {
                items.add(displayed.getItem());
            }
            categories.put(id, items);
        }
        //?} else {
        /*Map<CreativeModeTab, List<Item>> byTab = new IdentityHashMap<>();
        for (Item item : GameRegistries.ITEM) {
            CreativeModeTab tab = item.getItemCategory();
            if (tab != null) {
                byTab.computeIfAbsent(tab, ignored -> new ArrayList<>()).add(item);
            }
        }
        for (CreativeModeTab tab : categoryTabs()) {
            List<Item> items = byTab.get(tab);
            if (items != null) {
                categories.put(id(tab), items);
            }
        }
        *///?}
        return categories;
    }
}
