package com.andresblue.tristorage.block;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.StorageTier;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
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
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // Creative mode skips loot tables; explicitly preserve a populated core.
        if (!world.isClient() && player.isInCreativeMode()
                && world.getBlockEntity(pos) instanceof StorageCoreBlockEntity core
                && (core.totalItems() > 0L || core.installedChests() > 0)) {
            ItemStack portable = new ItemStack(asItem());
            portable.set(TriStorageMod.CORE_STORAGE, core.snapshot());
            if (!player.getInventory().insertStack(portable)) {
                player.dropItem(portable, false);
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (!world.isClient()) {
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

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(settings -> new StorageCoreBlock(tier, settings));
    }
}
