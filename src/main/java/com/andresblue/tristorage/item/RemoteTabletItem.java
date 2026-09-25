package com.andresblue.tristorage.item;

import com.andresblue.tristorage.storage.RemoteAccessManager;
import com.andresblue.tristorage.storage.RemoteTerminalMode;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
        if (world.isClientSide) {
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
        CompoundTag nbt = tablet.getOrCreateTag();
        nbt.putString(DIMENSION_KEY, world.dimension().location().toString());
        nbt.putLong(POSITION_KEY, linkerPos.asLong());
        nbt.putLong(CORE_POSITION_KEY, core.getBlockPos().asLong());
        player.displayClientMessage(Component.translatable("message.tristorage.tablet_linked"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack tablet = player.getItemInHand(hand);
        if (world.isClientSide) {
            return InteractionResultHolder.success(tablet);
        }
        CompoundTag nbt = tablet.getTag();
        if (nbt == null || !nbt.contains(DIMENSION_KEY) || !nbt.contains(POSITION_KEY)) {
            player.displayClientMessage(Component.translatable("message.tristorage.tablet_unlinked"), true);
            return InteractionResultHolder.fail(tablet);
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(nbt.getString(DIMENSION_KEY));
        if (dimensionId == null || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(tablet);
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel targetWorld = serverPlayer.getServer().getLevel(dimension);
        BlockPos linkerPos = BlockPos.of(nbt.getLong(POSITION_KEY));
        if (targetWorld == null) {
            player.displayClientMessage(Component.translatable("message.tristorage.remote_unavailable"), true);
            return InteractionResultHolder.fail(tablet);
        }

        BlockPos coreHint = nbt.contains(CORE_POSITION_KEY)
                ? BlockPos.of(nbt.getLong(CORE_POSITION_KEY))
                : null;
        RemoteAccessManager.request(serverPlayer, hand, targetWorld, linkerPos, coreHint, mode);
        return InteractionResultHolder.consume(tablet);
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
        CompoundTag nbt = stack.getTag();
        return nbt != null
                && hasValidLink(nbt)
                && dimension.location().toString().equals(nbt.getString(DIMENSION_KEY))
                && linkerPos.asLong() == nbt.getLong(POSITION_KEY);
    }

    /** Copies only a structurally valid TriStorage link, never arbitrary item NBT. */
    public static boolean copyValidatedLink(ItemStack source, ItemStack target) {
        CompoundTag sourceNbt = source.getTag();
        if (sourceNbt == null || !hasValidLink(sourceNbt)) {
            return false;
        }
        CompoundTag targetNbt = target.getOrCreateTag();
        targetNbt.putString(DIMENSION_KEY, sourceNbt.getString(DIMENSION_KEY));
        targetNbt.putLong(POSITION_KEY, sourceNbt.getLong(POSITION_KEY));
        if (sourceNbt.contains(CORE_POSITION_KEY, Tag.TAG_LONG)) {
            targetNbt.putLong(CORE_POSITION_KEY, sourceNbt.getLong(CORE_POSITION_KEY));
        }
        return true;
    }

    static boolean hasValidLink(CompoundTag nbt) {
        return nbt.contains(DIMENSION_KEY, Tag.TAG_STRING)
                && ResourceLocation.tryParse(nbt.getString(DIMENSION_KEY)) != null
                && nbt.contains(POSITION_KEY, Tag.TAG_LONG)
                && (!nbt.contains(CORE_POSITION_KEY)
                || nbt.contains(CORE_POSITION_KEY, Tag.TAG_LONG));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        return nbt != null && hasValidLink(nbt);
    }
}
