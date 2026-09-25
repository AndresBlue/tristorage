package com.andresblue.tristorage.block;

import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.StorageTier;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class StorageCoreBlock extends BlockWithEntity implements NetworkBlock {
    private final StorageTier tier;

    public StorageCoreBlock(StorageTier tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    public StorageTier tier() {
        return tier;
    }

    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player,
                                        BlockView world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof StorageCoreBlockEntity core
                && (core.totalItems() > 0 || core.installedChests() > 0)) {
            return 0.0f;
        }
        return super.calcBlockBreakingDelta(state, player, world, pos);
    }

    @Override
    public void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient
                && world.getBlockEntity(pos) instanceof StorageCoreBlockEntity core
                && (core.totalItems() > 0 || core.installedChests() > 0)) {
            player.sendMessage(new net.minecraft.text.TranslatableText(
                    "message.tristorage.core_not_empty"), true);
        }
        super.onBlockBreakStart(state, world, pos, player);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);
            if (factory != null) {
                player.openHandledScreen(factory);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StorageCoreBlockEntity(pos, state);
    }
}
