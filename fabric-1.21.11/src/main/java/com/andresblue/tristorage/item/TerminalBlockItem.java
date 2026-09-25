package com.andresblue.tristorage.item;

import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/** Warns when command-added data makes a terminal unsafe as an upgrade ingredient. */
public final class TerminalBlockItem extends BlockItem {
    public TerminalBlockItem(Block block, Item.Settings settings) {
        super(block, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context,
                              TooltipDisplayComponent display,
                              Consumer<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, display, tooltip, type);
        if (stack.contains(DataComponentTypes.BLOCK_ENTITY_DATA)
                || stack.contains(DataComponentTypes.CUSTOM_DATA)) {
            tooltip.accept(Text.translatable("tooltip.tristorage.terminal_nbt_blocked")
                    .formatted(Formatting.RED));
        }
    }
}
