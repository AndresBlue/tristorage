package com.andresblue.tristorage.item;

import com.andresblue.tristorage.version.Ids;
import com.andresblue.tristorage.version.Players;

import com.andresblue.tristorage.version.Nbt;

import com.andresblue.tristorage.storage.RemoteAccessManager;
import com.andresblue.tristorage.storage.RemoteTerminalMode;
import com.andresblue.tristorage.storage.StorageNetwork;
import com.andresblue.tristorage.version.Stacks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
//?}
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.block.LinkerBlock;

public final class RemoteTabletItem extends Item {
    private static final String DIMENSION_KEY = "LinkedDimension";
    private static final String POSITION_KEY = "LinkedPosition";
    private static final String CORE_POSITION_KEY = "LinkedCorePosition";

    private final RemoteTerminalMode mode;

    public RemoteTabletItem(Properties settings, RemoteTerminalMode mode) {
        super(settings);
        this.mode = mode;
    }

    public RemoteTerminalMode mode() {
        return mode;
    }

    public InteractionResult linkTo(Level world, BlockPos linkerPos, Player player, ItemStack tablet) {
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        StorageCoreBlockEntity core = StorageNetwork.findCore(world, linkerPos);
        if (core == null) {
            player.displayClientMessage(Component.translatable("message.tristorage.linker_offline"), true);
            return InteractionResult.CONSUME;
        }
        LinkerBlockEntity linker = LinkerBlock.getOrCreateLinkerEntity(
                world, linkerPos, world.getBlockState(linkerPos));
        if (!world.dimension().equals(Level.OVERWORLD)
                && (linker == null
                || !linker.isAntennaActive())) {
            player.displayClientMessage(Component.translatable(
                    "message.tristorage.remote_requires_antenna"), true);
            return InteractionResult.CONSUME;
        }
        CompoundTag nbt = Stacks.customData(tablet);
        nbt.putString(DIMENSION_KEY, world.dimension().location().toString());
        nbt.putLong(POSITION_KEY, linkerPos.asLong());
        nbt.putLong(CORE_POSITION_KEY, core.getBlockPos().asLong());
        Stacks.setCustomData(tablet, nbt);
        player.displayClientMessage(Component.translatable("message.tristorage.tablet_linked"), true);
        return InteractionResult.CONSUME;
    }

    //? if >=1.21.2 {
    /*@Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        return openRemoteTerminal(world, player, hand);
    }
    *///?} else {
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        return new InteractionResultHolder<>(openRemoteTerminal(world, player, hand),
                player.getItemInHand(hand));
    }
    //?}

    private InteractionResult openRemoteTerminal(Level world, Player player, InteractionHand hand) {
        ItemStack tablet = player.getItemInHand(hand);
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        CompoundTag nbt = Stacks.customData(tablet);
        if (!nbt.contains(DIMENSION_KEY) || !nbt.contains(POSITION_KEY)) {
            player.displayClientMessage(Component.translatable("message.tristorage.tablet_unlinked"), true);
            return InteractionResult.FAIL;
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(Nbt.getString(nbt, DIMENSION_KEY));
        if (dimensionId == null || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }
        ResourceKey<Level> dimension = Ids.dimension(dimensionId);
        ServerLevel targetWorld = Players.server(serverPlayer).getLevel(dimension);
        BlockPos linkerPos = BlockPos.of(Nbt.getLong(nbt, POSITION_KEY));
        if (targetWorld == null) {
            player.displayClientMessage(Component.translatable("message.tristorage.remote_unavailable"), true);
            return InteractionResult.FAIL;
        }

        BlockPos coreHint = nbt.contains(CORE_POSITION_KEY)
                ? BlockPos.of(Nbt.getLong(nbt, CORE_POSITION_KEY))
                : null;
        RemoteAccessManager.request(serverPlayer, hand, targetWorld, linkerPos, coreHint, mode);
        return InteractionResult.CONSUME;
    }

    public static boolean isLinkedTo(ItemStack stack, ResourceKey<Level> dimension,
                                     BlockPos linkerPos) {
        return isLinkedTo(stack, dimension, linkerPos, null);
    }

    public static boolean isLinkedTo(ItemStack stack, ResourceKey<Level> dimension,
                                     BlockPos linkerPos, RemoteTerminalMode requiredMode) {
        if (!(stack.getItem() instanceof RemoteTabletItem)) {
            return false;
        }
        RemoteTabletItem tablet = (RemoteTabletItem) stack.getItem();
        if (requiredMode != null && tablet.mode != requiredMode) {
            return false;
        }
        CompoundTag nbt = Stacks.customData(stack);
        return hasValidLink(nbt)
                && dimension.location().toString().equals(Nbt.getString(nbt, DIMENSION_KEY))
                && linkerPos.asLong() == Nbt.getLong(nbt, POSITION_KEY);
    }

    /** Copies only a structurally valid TriStorage link, never arbitrary item NBT. */
    public static boolean copyValidatedLink(ItemStack source, ItemStack target) {
        CompoundTag sourceNbt = Stacks.customData(source);
        if (!hasValidLink(sourceNbt)) {
            return false;
        }
        CompoundTag targetNbt = Stacks.customData(target);
        targetNbt.putString(DIMENSION_KEY, Nbt.getString(sourceNbt, DIMENSION_KEY));
        targetNbt.putLong(POSITION_KEY, Nbt.getLong(sourceNbt, POSITION_KEY));
        if (Nbt.hasLong(sourceNbt, CORE_POSITION_KEY)) {
            targetNbt.putLong(CORE_POSITION_KEY, Nbt.getLong(sourceNbt, CORE_POSITION_KEY));
        }
        Stacks.setCustomData(target, targetNbt);
        return true;
    }

    static boolean hasValidLink(CompoundTag nbt) {
        return Nbt.hasString(nbt, DIMENSION_KEY)
                && ResourceLocation.tryParse(Nbt.getString(nbt, DIMENSION_KEY)) != null
                && Nbt.hasLong(nbt, POSITION_KEY)
                && (!nbt.contains(CORE_POSITION_KEY)
                || Nbt.hasLong(nbt, CORE_POSITION_KEY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return Stacks.hasCustomData(stack) && hasValidLink(Stacks.customData(stack));
    }
}
