package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Categories derived from the real creative tabs or installed mod namespaces. */
public record StorageCategory(String id, Text title, ItemStack icon,
                              Predicate<ItemStack> predicate) {

    public static List<StorageCategory> build(
            Collection<StorageCoreBlockEntity.EntryView> entries) {
        return build(entries, TerminalFilter.CategoryMode.TYPE);
    }

    public static List<StorageCategory> build(
            Collection<StorageCoreBlockEntity.EntryView> entries,
            TerminalFilter.CategoryMode mode) {
        List<StorageCategory> result = new ArrayList<>();
        result.add(category(TerminalFilter.ALL, Text.translatable("category.tristorage.all"),
                new ItemStack(Items.CHEST), ignored -> true));

        if (mode == TerminalFilter.CategoryMode.MOD) {
            appendModCategories(result, entries);
        } else if (mode == TerminalFilter.CategoryMode.TYPE) {
            appendCreativeCategories(result, entries);
        }
        return List.copyOf(result);
    }

    private static void appendCreativeCategories(
            List<StorageCategory> result,
            Collection<StorageCoreBlockEntity.EntryView> entries) {
        List<ItemGroup> available = new ArrayList<>();
        for (ItemGroup group : ItemGroups.getGroups()) {
            if (group.getType() != ItemGroup.Type.CATEGORY || !group.hasStacks()) continue;
            if (entries.stream().noneMatch(entry -> group.contains(entry.stack()))) continue;
            available.add(group);
            String id = String.valueOf(Registries.ITEM_GROUP.getId(group));
            result.add(category(id, group.getDisplayName(), group.getIcon(), group::contains));
        }

        boolean hasUncategorized = entries.stream().anyMatch(entry ->
                available.stream().noneMatch(group -> group.contains(entry.stack())));
        if (hasUncategorized) {
            result.add(category(TerminalFilter.UNCATEGORIZED,
                    Text.translatable("category.tristorage.uncategorized"),
                    new ItemStack(Items.BARRIER), stack ->
                            available.stream().noneMatch(group -> group.contains(stack))));
        }
    }

    private static void appendModCategories(
            List<StorageCategory> result,
            Collection<StorageCoreBlockEntity.EntryView> entries) {
        Set<String> namespaces = new LinkedHashSet<>();
        entries.stream()
                .map(entry -> Registries.ITEM.getId(entry.stack().getItem()).getNamespace())
                .sorted()
                .forEach(namespaces::add);

        for (String namespace : namespaces) {
            ItemStack icon = entries.stream()
                    .filter(entry -> Registries.ITEM.getId(entry.stack().getItem())
                            .getNamespace().equals(namespace))
                    .map(StorageCoreBlockEntity.EntryView::stack)
                    .min(Comparator.comparing(stack ->
                            Registries.ITEM.getId(stack.getItem()).toString()))
                    .orElse(new ItemStack(Items.CHEST));
            String modName = FabricLoader.getInstance().getModContainer(namespace)
                    .map(container -> container.getMetadata().getName())
                    .orElse(namespace);
            result.add(category(namespace, Text.literal(modName), icon,
                    stack -> Registries.ITEM.getId(stack.getItem())
                            .getNamespace().equals(namespace)));
        }
    }

    public boolean matches(ItemStack stack) {
        return predicate.test(stack);
    }

    public static String searchText(ItemStack stack) {
        var id = Registries.ITEM.getId(stack.getItem());
        String modName = FabricLoader.getInstance().getModContainer(id.getNamespace())
                .map(container -> container.getMetadata().getName())
                .orElse(id.getNamespace());
        return TerminalFilter.normalize(String.join(" ",
                id.toString(), id.getNamespace(), modName,
                id.getPath().replace('_', ' '), stack.getName().getString()));
    }

    private static StorageCategory category(String id, Text title, ItemStack icon,
                                            Predicate<ItemStack> predicate) {
        ItemStack namedIcon = icon.copyWithCount(1);
        namedIcon.set(DataComponentTypes.CUSTOM_NAME, title);
        return new StorageCategory(id, title, namedIcon, predicate);
    }
}
