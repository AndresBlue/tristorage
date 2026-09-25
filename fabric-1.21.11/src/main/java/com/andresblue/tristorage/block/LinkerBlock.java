package com.andresblue.tristorage.block;

import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ItemScatterer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;

import org.jetbrains.annotations.Nullable;

public final class LinkerBlock extends BlockWithEntity implements NetworkBlock {
    public static final EnumProperty<AntennaMount> ANTENNA =
            EnumProperty.of("antenna", AntennaMount.class);

    public LinkerBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(ANTENNA, AntennaMount.NONE));
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack held, BlockState state, World world, BlockPos pos,
                                         PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (held.getItem() instanceof RemoteTabletItem tablet) {
            return tablet.linkTo(world, pos, player, held);
        }
        if (!world.isClient() && world.getBlockEntity(pos) instanceof LinkerBlockEntity linker) {
            linker.refreshAntennaMount();
            player.openHandledScreen(linker);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof LinkerBlockEntity linker) {
            linker.refreshAntennaMount();
            player.openHandledScreen(linker);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LinkerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient() ? null
                : validateTicker(type, com.andresblue.tristorage.TriStorageMod.LINKER_BLOCK_ENTITY,
                LinkerBlockEntity::serverTick);
    }

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        builder.add(ANTENNA);
    }

    public static AntennaMount findAntennaMount(World world, BlockPos pos) {
        return AntennaMount.firstAvailable(direction ->
                world.getBlockState(pos.offset(direction)).isAir());
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos,
                                  net.minecraft.block.Block sourceBlock,
                                  WireOrientation orientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, orientation, notify);
        if (!world.isClient() && world.getBlockEntity(pos) instanceof LinkerBlockEntity linker) {
            linker.refreshAntennaMount();
        }
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        if (world.getBlockEntity(pos) instanceof LinkerBlockEntity linker) {
            ItemScatterer.spawn(world, pos, linker);
            world.updateComparators(pos, this);
        }
        super.onStateReplaced(state, world, pos, moved);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(LinkerBlock::new);
    }
}
