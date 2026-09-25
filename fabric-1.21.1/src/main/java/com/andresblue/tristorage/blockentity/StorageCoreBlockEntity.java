package com.andresblue.tristorage.blockentity;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.StorageCoreBlock;
import com.andresblue.tristorage.screen.CoreScreenHandler;
import com.andresblue.tristorage.storage.StorageTier;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StorageCoreBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {
    private static final String CHESTS_KEY = "InstalledChests";
    private static final String ENTRIES_KEY = "Entries";

    private final Map<String, StoredEntry> entries = new LinkedHashMap<>();
    private int installedChests;
    private long totalItems;
    private int revision;

    public StorageCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TriStorageMod.STORAGE_CORE_BLOCK_ENTITY, pos, state);
    }

    public StorageTier tier() {
        if (getCachedState().getBlock() instanceof StorageCoreBlock core) {
            return core.tier();
        }
        return StorageTier.IRON;
    }

    public int installedChests() {
        return installedChests;
    }

    public int maxChests() {
        return tier().chestCapacity();
    }

    public int typeCapacity() {
        return tier().typeCapacity(installedChests);
    }

    public long itemCapacity() {
        return tier().itemCapacity(installedChests);
    }

    public int storedTypes() {
        return entries.size();
    }

    public long totalItems() {
        return totalItems;
    }

    public int revision() {
        return revision;
    }

    public int addChests(int requested) {
        int accepted = Math.min(Math.max(0, requested), maxChests() - installedChests);
        if (accepted > 0) {
            installedChests += accepted;
            changed();
        }
        return accepted;
    }

    public int removableChests(int requested) {
        int removable = Math.min(Math.max(0, requested), installedChests);
        while (removable > 0) {
            int remaining = installedChests - removable;
            if (entries.size() <= tier().typeCapacity(remaining)
                    && totalItems <= tier().itemCapacity(remaining)) {
                return removable;
            }
            removable--;
        }
        return 0;
    }

    public int removeChests(int requested) {
        int removed = removableChests(requested);
        if (removed > 0) {
            installedChests -= removed;
            changed();
        }
        return removed;
    }

    public long insert(ItemStack source, long requested) {
        if (source.isEmpty() || requested <= 0 || installedChests <= 0) {
            return 0;
        }
        String key = keyOf(source);
        StoredEntry existing = entries.get(key);
        if (existing == null && entries.size() >= typeCapacity()) {
            return 0;
        }
        long accepted = Math.min(requested, itemCapacity() - totalItems);
        if (accepted <= 0) {
            return 0;
        }
        if (existing == null) {
            ItemStack template = source.copy();
            template.setCount(1);
            entries.put(key, new StoredEntry(template, accepted));
        } else {
            existing.count += accepted;
        }
        totalItems += accepted;
        changed();
        return accepted;
    }

    public ItemStack extract(String key, int requested) {
        StoredEntry existing = entries.get(key);
        if (existing == null || requested <= 0) {
            return ItemStack.EMPTY;
        }
        int extracted = (int) Math.min(Math.min(existing.count, requested), existing.template.getMaxCount());
        ItemStack result = existing.template.copy();
        result.setCount(extracted);
        existing.count -= extracted;
        totalItems -= extracted;
        if (existing.count <= 0) {
            entries.remove(key);
        }
        changed();
        return result;
    }

    public List<EntryView> page(int page, int pageSize) {
        int safePage = Math.max(0, page);
        int start = safePage * pageSize;
        if (start >= entries.size()) {
            return List.of();
        }
        List<EntryView> result = new ArrayList<>(pageSize);
        int index = 0;
        for (Map.Entry<String, StoredEntry> mapEntry : entries.entrySet()) {
            if (index >= start && result.size() < pageSize) {
                StoredEntry value = mapEntry.getValue();
                result.add(new EntryView(mapEntry.getKey(), value.template.copy(), value.count));
            }
            if (result.size() >= pageSize) {
                break;
            }
            index++;
        }
        return result;
    }

    public int pageCount(int pageSize) {
        return Math.max(1, (entries.size() + pageSize - 1) / pageSize);
    }

    public Collection<EntryView> allEntries() {
        List<EntryView> result = new ArrayList<>(entries.size());
        entries.forEach((key, value) ->
                result.add(new EntryView(key, value.template.copy(), value.count)));
        return result;
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("screen.tristorage.core", tier().level());
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CoreScreenHandler(syncId, playerInventory, this);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt(CHESTS_KEY, installedChests);
        NbtList list = new NbtList();
        for (StoredEntry entry : entries.values()) {
            NbtCompound stored = new NbtCompound();
            stored.put("Stack", entry.template.encode(registries));
            stored.putLong("Count", entry.count);
            list.add(stored);
        }
        nbt.put(ENTRIES_KEY, list);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        installedChests = Math.min(Math.max(0, nbt.getInt(CHESTS_KEY)), maxChests());
        entries.clear();
        totalItems = 0;
        NbtList list = nbt.getList(ENTRIES_KEY, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound stored = list.getCompound(i);
            ItemStack stack = ItemStack.fromNbtOrEmpty(registries, stored.getCompound("Stack"));
            long count = Math.max(0, stored.getLong("Count"));
            if (!stack.isEmpty() && count > 0) {
                stack.setCount(1);
                entries.put(keyOf(stack), new StoredEntry(stack, count));
                totalItems += count;
            }
        }
        revision++;
    }

    private static String keyOf(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id + "|" + stack.getComponentChanges();
    }

    private void changed() {
        revision++;
        markDirty();
    }

    private static final class StoredEntry {
        private final ItemStack template;
        private long count;

        private StoredEntry(ItemStack template, long count) {
            this.template = template;
            this.count = count;
        }
    }

    public record EntryView(String key, ItemStack stack, long count) {
    }
}
