package com.andresblue.tristorage.block;

import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class LinkerBlock extends BaseEntityBlock implements NetworkBlock {
    public static final EnumProperty<AntennaMount> ANTENNA =
            EnumProperty.create("antenna", AntennaMount.class);

    public LinkerBlock(Properties settings) {
        super(settings);
        registerDefaultState(getStateDefinition().any().setValue(ANTENNA, AntennaMount.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ANTENNA);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        StorageNetwork.markTopologyChanged(world);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                              InteractionHand hand, BlockHitResult hit) {
        getOrCreateLinkerEntity(world, pos, state);
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof RemoteTabletItem tablet) {
            return tablet.linkTo(world, pos, player, held);
        }
        if (!world.isClientSide) {
            MenuProvider factory = state.getMenuProvider(world, pos);
            if (factory != null) {
                player.openMenu(factory);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos,
                               Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborChanged(state, world, pos, sourceBlock, sourcePos, notify);
        LinkerBlockEntity linker = getOrCreateLinkerEntity(world, pos, state);
        if (!world.isClientSide && linker != null) {
            linker.refreshAntennaMount();
        }
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && world.getBlockEntity(pos) instanceof LinkerBlockEntity linker) {
            Containers.dropContents(world, pos, linker);
            world.updateNeighbourForOutputSignal(pos, this);
            StorageNetwork.markTopologyChanged(world);
        }
        super.onRemove(state, world, pos, newState, moved);
    }

    public static AntennaMount findAntennaMount(LevelAccessor world, BlockPos pos) {
        return AntennaMount.firstAvailable(direction ->
                world.getBlockState(pos.relative(direction)).isAir());
    }

    /** Adds the new 1.9 block entity lazily for Linkers saved by older versions. */
    @Nullable
    public static LinkerBlockEntity getOrCreateLinkerEntity(
            Level world, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(pos) instanceof LinkerBlockEntity existing) {
            return existing;
        }
        if (world.isClientSide || !(state.getBlock() instanceof LinkerBlock)) {
            return null;
        }
        LinkerBlockEntity created = new LinkerBlockEntity(pos, state);
        world.setBlockEntity(created);
        return created;
    }
}
