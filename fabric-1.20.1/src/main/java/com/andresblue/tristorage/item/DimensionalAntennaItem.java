package com.andresblue.tristorage.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DimensionalAntennaItem extends Item {
    public DimensionalAntennaItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world,
                              List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.tristorage.antenna_install")
                .formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.tristorage.antenna_air")
                .formatted(Formatting.GRAY));
    }
}
