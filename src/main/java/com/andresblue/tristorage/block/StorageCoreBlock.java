package com.andresblue.tristorage.block;

import com.andresblue.tristorage.version.Players;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.StorageTier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.andresblue.tristorage.storage.PortableCoreData;
import com.andresblue.tristorage.storage.StorageNetwork;
import org.jetbrains.annotations.Nullable;
//? if >=1.20.5 {
/*import com.mojang.serialization.MapCodec;
*///?}

public final class StorageCoreBlock extends BaseEntityBlock implements NetworkBlock {
    private final StorageTier tier;

    public StorageCoreBlock(StorageTier tier, Properties settings) {
        super(settings);
        this.tier = tier;
    }

    public StorageTier tier() {
        return tier;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        StorageNetwork.markTopologyChanged(world);
    }

    //? if >=1.20.3 {
    /*@Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state,
                                        Player player) {
        giveCreativePortable(world, pos, player);
        return super.playerWillDestroy(world, pos, state, player);
    }
    *///?} else {
    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        giveCreativePortable(world, pos, player);
        super.playerWillDestroy(world, pos, state, player);
    }
    //?}

    // Creative breaks drop nothing, so the player receives the portable Core directly.
    private void giveCreativePortable(Level world, BlockPos pos, Player player) {
        StorageCoreBlockEntity core = !world.isClientSide()
                && world.getBlockEntity(pos) instanceof StorageCoreBlockEntity found
                ? found : null;
        StorageNetwork.markTopologyChanged(world);
        if (core != null && player.isCreative()
                && (core.totalItems() > 0 || core.installedChests() > 0)) {
            ItemStack portable = new ItemStack(asItem());
            PortableCoreData.applyTo(portable, core.portableData());
            if (!player.getInventory().add(portable)) {
                player.drop(portable, false);
            }
        }
    }

    /**
     * Without a suitable tool vanilla drops nothing, and the dropped Core item
     * is what carries the storage. Refuse to make progress instead of letting
     * the storage end up orphaned.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player,
                                        BlockGetter world, BlockPos pos) {
        if (!canBreakSafely(player, state)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, world, pos);
    }

    public static boolean canBreakSafely(Player player, BlockState state) {
        return player.isCreative() || player.hasCorrectToolForDrops(state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    //? if >=1.20.5 {
    /*@Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return MapCodec.unit(this);
    }
    *///?}

    //? if >=1.20.5 {
    /*@Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos,
                                               Player player, BlockHitResult hit) {
    *///?} else {
    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
    //?}
        if (!world.isClientSide()) {
            if (world.getBlockEntity(pos) instanceof StorageCoreBlockEntity core
                    && core.isRecoveryRequired()) {
                Players.actionBar(player, Component.translatable(
                        "message.tristorage.core_recovery_required"));
                return InteractionResult.CONSUME;
            }
            if (world.getBlockEntity(pos) instanceof StorageCoreBlockEntity core
                    && !core.runtime().isReady()) {
                Players.actionBar(player, Component.translatable(
                        "message.tristorage.core_not_ready"));
                return InteractionResult.CONSUME;
            }
            MenuProvider factory = state.getMenuProvider(world, pos);
            if (factory != null) {
                player.openMenu(factory);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageCoreBlockEntity(pos, state);
    }
}
