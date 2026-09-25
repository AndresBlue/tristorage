package com.andresblue.tristorage.item;

import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public final class StorageCoreBlockItem extends BlockItem {
    private static final String PAYLOAD = "TriStorageCore";
    public StorageCoreBlockItem(Block block, Item.Properties properties) { super(block, properties.stacksTo(1)); }

    public static void writeToStack(ItemStack stack, StorageCoreBlockEntity core, HolderLookup.Provider registries) {
        CompoundTag payload = new CompoundTag();
        core.writePortable(payload, registries);
        if (payload.getInt("InstalledChests") > 0 || payload.contains("Entries")) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(PAYLOAD, payload));
        }
    }

    public static void readFromStack(StorageCoreBlockEntity core, ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag legacy = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        CompoundTag source = custom.contains(PAYLOAD) ? custom.getCompound(PAYLOAD) : custom;
        if (!source.contains("InstalledChests") && !source.contains("Entries")) source = legacy.contains(PAYLOAD) ? legacy.getCompound(PAYLOAD) : legacy;
        if (source.contains("InstalledChests") || source.contains("Entries")) core.readPortable(source, registries);
    }

    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                           List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CompoundTag custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag payload = custom.contains(PAYLOAD) ? custom.getCompound(PAYLOAD) : custom;
        int chests = payload.getInt("InstalledChests");
        tooltip.add(Component.translatable(chests > 0 || payload.contains("Entries")
                ? "tooltip.tristorage.core_offline" : "tooltip.tristorage.core_empty"));
        if (chests > 0) tooltip.add(Component.translatable("tooltip.tristorage.core_chests", chests));
    }
}
