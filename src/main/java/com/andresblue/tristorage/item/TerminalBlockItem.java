package com.andresblue.tristorage.item;

import com.andresblue.tristorage.recipe.TerminalUpgradeSafety;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class TerminalBlockItem extends BlockItem {
    public TerminalBlockItem(Block block, Properties settings) {
        super(block, settings);
    }

    //? if >=1.20.5 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext world,
                                List<Component> tooltip, TooltipFlag context) {
    *///?} else {
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world,
                                List<Component> tooltip, TooltipFlag context) {
    //?}
        super.appendHoverText(stack, world, tooltip, context);
        if (!TerminalUpgradeSafety.isClean(stack)) {
            tooltip.add(Component.translatable("tooltip.tristorage.terminal_nbt_blocked")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
