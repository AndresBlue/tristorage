package com.andresblue.tristorage.item;

import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

public final class DimensionalAntennaItem extends Item {
    public DimensionalAntennaItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              TooltipDisplayComponent display,
                              Consumer<Text> tooltip, TooltipType type) {
        tooltip.accept(Text.translatable("tooltip.tristorage.antenna_install")
                .formatted(Formatting.LIGHT_PURPLE));
        tooltip.accept(Text.translatable("tooltip.tristorage.antenna_air")
                .formatted(Formatting.DARK_GRAY));
    }
}
