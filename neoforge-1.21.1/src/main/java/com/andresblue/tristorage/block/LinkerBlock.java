package com.andresblue.tristorage.block;

import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class LinkerBlock extends BaseEntityBlock implements NetworkBlock {
    public static final EnumProperty<AntennaMount> ANTENNA =
            EnumProperty.create("antenna", AntennaMount.class);

    public LinkerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ANTENNA, AntennaMount.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ANTENNA);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return MapCodec.unit(this);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkerBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        getOrCreateLinkerEntity(level, pos, state);
        if (held.getItem() instanceof RemoteTabletItem tablet) {
            return switch (tablet.linkTo(level, pos, player, held)) {
                case SUCCESS -> ItemInteractionResult.SUCCESS;
                case CONSUME, CONSUME_PARTIAL -> ItemInteractionResult.CONSUME;
                case FAIL -> ItemInteractionResult.FAIL;
                default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            };
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            MenuProvider factory = getOrCreateLinkerEntity(level, pos, state);
            if (factory != null) {
                player.openMenu(factory);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborChanged(state, level, pos, sourceBlock, sourcePos, notify);
        LinkerBlockEntity linker = getOrCreateLinkerEntity(level, pos, state);
        if (!level.isClientSide && linker != null) {
            linker.refreshAntennaMount();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof LinkerBlockEntity linker) {
            Containers.dropContents(level, pos, linker);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    public static AntennaMount findAntennaMount(LevelAccessor level, BlockPos pos) {
        return AntennaMount.firstAvailable(direction ->
                level.getBlockState(pos.relative(direction)).isAir());
    }

    /** Adds the block entity lazily for Linkers saved by older versions. */
    @Nullable
    public static LinkerBlockEntity getOrCreateLinkerEntity(
            Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof LinkerBlockEntity existing) {
            return existing;
        }
        if (level.isClientSide || !(state.getBlock() instanceof LinkerBlock)) {
            return null;
        }
        LinkerBlockEntity created = new LinkerBlockEntity(pos, state);
        level.setBlockEntity(created);
        return created;
    }
}
