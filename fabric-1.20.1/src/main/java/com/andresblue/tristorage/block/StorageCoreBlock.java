package com.andresblue.tristorage.block;

import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.StorageTier;
import com.andresblue.tristorage.storage.PortableCoreData;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.text.Text;
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
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        StorageNetwork.markTopologyChanged(world);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        StorageCoreBlockEntity core = !world.isClient
                && world.getBlockEntity(pos) instanceof StorageCoreBlockEntity found
                ? found : null;
        StorageNetwork.markTopologyChanged(world);
        if (core != null && player.isCreative()
                && (core.totalItems() > 0 || core.installedChests() > 0)) {
            net.minecraft.item.ItemStack portable = new net.minecraft.item.ItemStack(asItem());
            PortableCoreData.applyTo(portable, core.createNbt());
            if (!player.getInventory().insertStack(portable)) {
                player.dropItem(portable, false);
            }
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            if (world.getBlockEntity(pos) instanceof StorageCoreBlockEntity core
                    && core.isRecoveryRequired()) {
                player.sendMessage(Text.translatable(
                        "message.tristorage.core_recovery_required"), true);
                return ActionResult.CONSUME;
            }
            if (world.getBlockEntity(pos) instanceof StorageCoreBlockEntity core
                    && !core.runtime().isReady()) {
                player.sendMessage(Text.translatable(
                        "message.tristorage.core_not_ready"), true);
                return ActionResult.CONSUME;
            }
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
