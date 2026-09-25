package com.andresblue.tristorage.item;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class DimensionalAntennaItem extends Item {
    public DimensionalAntennaItem(Properties settings) {
        super(settings);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world,
                              List<Component> tooltip, TooltipFlag context) {
        tooltip.add(Component.translatable("tooltip.tristorage.antenna_install")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.tristorage.antenna_air")
                .withStyle(ChatFormatting.GRAY));
    }
}
