package com.andresblue.tristorage.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;

import java.util.List;

/** Portable contents copied between a placed core and its dropped item. */
public record CoreStorageData(int installedChests, List<StoredStack> entries) {
    public static final Codec<CoreStorageData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("installed_chests", 0)
                    .forGetter(CoreStorageData::installedChests),
            StoredStack.CODEC.listOf().optionalFieldOf("entries", List.of())
                    .forGetter(CoreStorageData::entries)
    ).apply(instance, CoreStorageData::new));

    public CoreStorageData {
        installedChests = Math.max(0, installedChests);
        entries = List.copyOf(entries);
    }

    public record StoredStack(ItemStack stack, long count) {
        public static final Codec<StoredStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.CODEC.fieldOf("stack").forGetter(StoredStack::stack),
                Codec.LONG.fieldOf("count").forGetter(StoredStack::count)
        ).apply(instance, StoredStack::new));

        public StoredStack {
            stack = stack.copyWithCount(1);
            count = Math.max(0L, count);
        }
    }
}
