package com.andresblue.tristorage.item;

import com.andresblue.tristorage.storage.PortableCoreData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class StorageCoreBlockItem extends BlockItem {
    public StorageCoreBlockItem(Block block, Properties settings) {
        super(block, settings.stacksTo(1));
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        // Cores from the NeoForge 1.21.1 port kept their storage in custom data.
        PortableCoreData.adoptPortLayout(context.getItemInHand());
        return super.place(context);
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
        int chests = PortableCoreData.installedChests(stack);
        int types = PortableCoreData.storedTypes(stack);
        long items = PortableCoreData.totalItems(stack);
        if (chests == 0 && types == 0 && items == 0) {
            tooltip.add(Component.translatable("tooltip.tristorage.core_empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.tristorage.core_offline")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.tristorage.core_chests", chests)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tristorage.core_contents", types, items)
                .withStyle(ChatFormatting.GRAY));
    }
}
