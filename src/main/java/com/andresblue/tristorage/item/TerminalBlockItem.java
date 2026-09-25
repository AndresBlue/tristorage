package com.andresblue.tristorage.item;

import com.andresblue.tristorage.recipe.TerminalUpgradeSafety;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
//? if >=1.21.5 {
/*import net.minecraft.world.item.component.TooltipDisplay;
*///?}

public final class TerminalBlockItem extends BlockItem {
    public TerminalBlockItem(Block block, Properties settings) {
        super(block, settings);
    }

    //? if >=1.21.5 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext world, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag context) {
        super.appendHoverText(stack, world, display, tooltip, context);
        describe(stack, tooltip);
    }
    *///?} elif >=1.20.5 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext world,
                                List<Component> tooltip, TooltipFlag context) {
        super.appendHoverText(stack, world, tooltip, context);
        describe(stack, tooltip::add);
    }
    *///?} else {
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world,
                                List<Component> tooltip, TooltipFlag context) {
        super.appendHoverText(stack, world, tooltip, context);
        describe(stack, tooltip::add);
    }
    //?}

    private static void describe(ItemStack stack, Consumer<Component> tooltip) {
        if (!TerminalUpgradeSafety.isClean(stack)) {
            tooltip.accept(Component.translatable("tooltip.tristorage.terminal_nbt_blocked")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
