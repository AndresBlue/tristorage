package com.andresblue.tristorage.item;

import com.andresblue.tristorage.storage.PortableCoreData;
import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class StorageCoreBlockItem extends BlockItem {
    public StorageCoreBlockItem(Block block, Settings settings) {
        super(block, settings.maxCount(1));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);
        int chests = PortableCoreData.installedChests(stack);
        int types = PortableCoreData.storedTypes(stack);
        long items = PortableCoreData.totalItems(stack);
        if (chests == 0 && types == 0 && items == 0) {
            tooltip.add(Text.translatable("tooltip.tristorage.core_empty")
                    .formatted(Formatting.DARK_GRAY));
            return;
        }
        tooltip.add(Text.translatable("tooltip.tristorage.core_offline")
                .formatted(Formatting.YELLOW));
        tooltip.add(Text.translatable("tooltip.tristorage.core_chests", chests)
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.tristorage.core_contents", types, items)
                .formatted(Formatting.GRAY));
    }
}
