package com.andresblue.tristorage.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class DimensionalAntennaItem extends Item {
    public DimensionalAntennaItem(Properties properties) { super(properties.stacksTo(16)); }
    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                           List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.tristorage.antenna_install"));
        tooltip.add(Component.translatable("tooltip.tristorage.antenna_air"));
    }
}
