package com.andresblue.tristorage.version;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
//? if >=1.20.5 {
/*import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
*///?}

/**
 * ItemStack operations whose API changed when item data moved from an NBT tag
 * to data components in Minecraft 1.20.5. Storage code calls these instead of
 * touching either representation directly.
 */
public final class Stacks {
    private Stacks() {
    }

    /**
     * Everything besides item and count that makes two stacks different: the
     * NBT tag before 1.20.5, the component patch after. Values are compared
     * with {@code equals}. With {@code copy}, the result may be retained.
     */
    @Nullable
    public static Object dataIdentity(ItemStack stack, boolean copy) {
        //? if >=1.20.5 {
        /*DataComponentPatch patch = stack.getComponentsPatch();
        return patch.isEmpty() ? null : patch;
        *///?} else {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : (copy ? tag.copy() : tag);
        //?}
    }

    /**
     * A hash of the stack's data that the client and the server compute
     * alike. Component values may hash by identity, so from 1.20.5 on only
     * the patched component types and a few plain values take part.
     */
    public static int stableDataHash(ItemStack stack) {
        //? if >=1.20.5 {
        /*int hash = 0;
        for (var entry : stack.getComponentsPatch().entrySet()) {
            int typeId = BuiltInRegistries.DATA_COMPONENT_TYPE.getId(entry.getKey());
            Object value = entry.getValue().orElse(null);
            int valueHash = value instanceof Integer || value instanceof CustomData
                    ? value.hashCode() : (value == null ? 0 : 1);
            // Summed, because the patch does not iterate in a fixed order.
            hash += 31 * typeId + valueHash;
        }
        return hash;
        *///?} else {
        return java.util.Objects.hashCode(stack.getTag());
        //?}
    }

    /** Rebuilds a single stack from an item and a {@link #dataIdentity} value. */
    public static ItemStack withData(Item item, @Nullable Object data) {
        //? if >=1.20.5 {
        /*return data == null
                ? new ItemStack(item)
                : new ItemStack(item.builtInRegistryHolder(), 1, (DataComponentPatch) data);
        *///?} else {
        ItemStack stack = new ItemStack(item);
        if (data != null) {
            stack.setTag(((CompoundTag) data).copy());
        }
        return stack;
        //?}
    }

    public static boolean sameItemSameData(ItemStack first, ItemStack second) {
        //? if >=1.20.5 {
        /*return ItemStack.isSameItemSameComponents(first, second);
        *///?} else {
        return ItemStack.isSameItemSameTags(first, second);
        //?}
    }

    public static CompoundTag save(ItemStack stack, HolderLookup.Provider registries) {
        //? if >=1.20.5 {
        /*return (CompoundTag) stack.saveOptional(registries);
        *///?} else {
        return stack.save(new CompoundTag());
        //?}
    }

    public static ItemStack parse(CompoundTag tag, HolderLookup.Provider registries) {
        //? if >=1.20.5 {
        /*return ItemStack.parseOptional(registries, tag);
        *///?} else {
        return ItemStack.of(tag);
        //?}
    }

    /** A copy of the stack's custom data; empty when it has none. */
    public static CompoundTag customData(ItemStack stack) {
        //? if >=1.20.5 {
        /*return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        *///?} else {
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag.copy();
        //?}
    }

    public static boolean hasCustomData(ItemStack stack) {
        //? if >=1.20.5 {
        /*return stack.has(DataComponents.CUSTOM_DATA);
        *///?} else {
        return stack.hasTag();
        //?}
    }

    /** Replaces the stack's custom data; an empty tag removes it. */
    public static void setCustomData(ItemStack stack, CompoundTag tag) {
        //? if >=1.20.5 {
        /*if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        *///?} else {
        stack.setTag(tag.isEmpty() ? null : tag.copy());
        //?}
    }

    /** Block entity data an item carries into the world, or null. */
    @Nullable
    public static CompoundTag blockEntityData(ItemStack stack) {
        //? if >=1.20.5 {
        /*CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data == null ? null : data.copyTag();
        *///?} else {
        return BlockItem.getBlockEntityData(stack);
        //?}
    }

    public static boolean hasBlockEntityData(ItemStack stack) {
        return blockEntityData(stack) != null;
    }

    public static void setBlockEntityData(ItemStack stack, BlockEntityType<?> type,
                                          CompoundTag tag) {
        BlockItem.setBlockEntityData(stack, type, tag);
    }
}
