package com.andresblue.tristorage.item;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
//? if >=1.21.5 {
/*import net.minecraft.world.item.component.TooltipDisplay;
*///?}

public final class DimensionalAntennaItem extends Item {
    public DimensionalAntennaItem(Properties settings) {
        super(settings);
    }

    //? if >=1.21.5 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext world, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag context) {
        describe(tooltip);
    }
    *///?} elif >=1.20.5 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext world,
                                List<Component> tooltip, TooltipFlag context) {
        describe(tooltip::add);
    }
    *///?} else {
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world,
                                List<Component> tooltip, TooltipFlag context) {
        describe(tooltip::add);
    }
    //?}

    private static void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.tristorage.antenna_install")
                .withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("tooltip.tristorage.antenna_air")
                .withStyle(ChatFormatting.GRAY));
    }
}
