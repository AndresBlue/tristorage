package com.andresblue.tristorage.item;

import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.RemoteAccessManager;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class RemoteTabletItem extends Item {
    private static final String DIMENSION_KEY = "LinkedDimension";
    private static final String POSITION_KEY = "LinkedPosition";
    private static final String CORE_POSITION_KEY = "LinkedCorePosition";

    public RemoteTabletItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public InteractionResult linkTo(Level level, BlockPos linkerPos, Player player, ItemStack tablet) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        StorageCoreBlockEntity core = StorageNetwork.findCore(level, linkerPos);
        if (core == null) {
            player.displayClientMessage(Component.translatable("message.tristorage.linker_offline"), true);
            return InteractionResult.CONSUME;
        }
        LinkerBlockEntity linker = LinkerBlock.getOrCreateLinkerEntity(
                level, linkerPos, level.getBlockState(linkerPos));
        if (!level.dimension().equals(Level.OVERWORLD)
                && (linker == null
                || !linker.isAntennaActive())) {
            player.displayClientMessage(Component.translatable(
                    "message.tristorage.remote_requires_antenna"), true);
            return InteractionResult.CONSUME;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, tablet, tag -> {
            tag.putString(DIMENSION_KEY, level.dimension().location().toString());
            tag.putLong(POSITION_KEY, linkerPos.asLong());
            tag.putLong(CORE_POSITION_KEY, core.getBlockPos().asLong());
        });
        player.displayClientMessage(Component.translatable("message.tristorage.tablet_linked"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tablet = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(tablet);
        }
        CompoundTag tag = linkTag(tablet);
        if (tag == null) {
            player.displayClientMessage(Component.translatable("message.tristorage.tablet_unlinked"), true);
            return InteractionResultHolder.fail(tablet);
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION_KEY));
        if (dimensionId == null || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(tablet);
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel targetWorld = serverPlayer.getServer().getLevel(dimension);
        BlockPos linkerPos = BlockPos.of(tag.getLong(POSITION_KEY));
        if (targetWorld == null) {
            player.displayClientMessage(Component.translatable("message.tristorage.remote_unavailable"), true);
            return InteractionResultHolder.fail(tablet);
        }

        BlockPos coreHint = tag.contains(CORE_POSITION_KEY)
                ? BlockPos.of(tag.getLong(CORE_POSITION_KEY))
                : null;
        RemoteAccessManager.request(serverPlayer, hand, targetWorld, linkerPos, coreHint);
        return InteractionResultHolder.consume(tablet);
    }

    public static boolean isLinkedTo(ItemStack stack, ResourceKey<Level> dimension,
                                     BlockPos linkerPos) {
        if (!(stack.getItem() instanceof RemoteTabletItem)) {
            return false;
        }
        CompoundTag tag = linkTag(stack);
        return tag != null
                && dimension.location().toString().equals(tag.getString(DIMENSION_KEY))
                && linkerPos.asLong() == tag.getLong(POSITION_KEY);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return linkTag(stack) != null;
    }

    private static CompoundTag linkTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(DIMENSION_KEY) && tag.contains(POSITION_KEY) ? tag : null;
    }
}
