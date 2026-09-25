package com.andresblue.tristorage.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class TerminalBlockItem extends BlockItem {
    public TerminalBlockItem(Block block, Item.Properties properties) { super(block, properties); }
    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                           List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (stack.has(DataComponents.BLOCK_ENTITY_DATA) || stack.has(DataComponents.CUSTOM_DATA)) {
            tooltip.add(Component.translatable("tooltip.tristorage.terminal_nbt_blocked"));
        }
    }
}
