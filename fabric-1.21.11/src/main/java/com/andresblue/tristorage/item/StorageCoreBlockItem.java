package com.andresblue.tristorage.item;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.storage.CoreStorageData;
import net.minecraft.block.Block;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/** A non-stackable core item that visibly carries its complete storage payload. */
public final class StorageCoreBlockItem extends BlockItem {
    public StorageCoreBlockItem(Block block, Item.Settings settings) {
        super(block, settings.maxCount(1));
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context,
                              TooltipDisplayComponent display,
                              Consumer<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, display, tooltip, type);
        CoreStorageData data = stack.get(TriStorageMod.CORE_STORAGE);
        int chests = data == null ? 0 : data.installedChests();
        int types = data == null ? 0 : data.entries().size();
        long items = data == null ? 0L : data.entries().stream()
                .mapToLong(CoreStorageData.StoredStack::count)
                .reduce(0L, StorageCoreBlockItem::saturatedAdd);
        if (chests == 0 && types == 0 && items == 0L) {
            tooltip.accept(Text.translatable("tooltip.tristorage.core_empty")
                    .formatted(Formatting.GRAY));
        } else {
            tooltip.accept(Text.translatable("tooltip.tristorage.core_offline")
                    .formatted(Formatting.YELLOW));
            tooltip.accept(Text.translatable("tooltip.tristorage.core_chests", chests)
                    .formatted(Formatting.DARK_GRAY));
            tooltip.accept(Text.translatable("tooltip.tristorage.core_contents", types, items)
                    .formatted(Formatting.DARK_GRAY));
        }
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
