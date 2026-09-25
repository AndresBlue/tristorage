package com.andresblue.tristorage.block;

import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public final class LinkerBlock extends BlockWithEntity implements NetworkBlock {
    public static final EnumProperty<AntennaMount> ANTENNA =
            EnumProperty.of("antenna", AntennaMount.class);

    public LinkerBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(ANTENNA, AntennaMount.NONE));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ANTENNA);
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

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        StorageNetwork.markTopologyChanged(world);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        getOrCreateLinkerEntity(world, pos, state);
        ItemStack held = player.getStackInHand(hand);
        if (held.getItem() instanceof RemoteTabletItem tablet) {
            return tablet.linkTo(world, pos, player, held);
        }
        if (!world.isClient) {
            NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);
            if (factory != null) {
                player.openHandledScreen(factory);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos,
                               Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        LinkerBlockEntity linker = getOrCreateLinkerEntity(world, pos, state);
        if (!world.isClient && linker != null) {
            linker.refreshAntennaMount();
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())
                && world.getBlockEntity(pos) instanceof LinkerBlockEntity linker) {
            ItemScatterer.spawn(world, pos, linker);
            world.updateComparators(pos, this);
            StorageNetwork.markTopologyChanged(world);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    public static AntennaMount findAntennaMount(WorldAccess world, BlockPos pos) {
        return AntennaMount.firstAvailable(direction ->
                world.getBlockState(pos.offset(direction)).isAir());
    }

    /** Adds the new 1.9 block entity lazily for Linkers saved by older versions. */
    @Nullable
    public static LinkerBlockEntity getOrCreateLinkerEntity(
            World world, BlockPos pos, BlockState state) {
        if (world.getBlockEntity(pos) instanceof LinkerBlockEntity existing) {
            return existing;
        }
        if (world.isClient || !(state.getBlock() instanceof LinkerBlock)) {
            return null;
        }
        LinkerBlockEntity created = new LinkerBlockEntity(pos, state);
        world.addBlockEntity(created);
        return created;
    }
}
