package com.andresblue.tristorage.block;

import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.StorageNetwork;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class TerminalBlock extends Block implements NetworkBlock {
    public TerminalBlock(Properties settings) {
        super(settings);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(world, pos, state, placer, itemStack);
        StorageNetwork.markTopologyChanged(world);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            StorageNetwork.markTopologyChanged(world);
        }
        super.onRemove(state, world, pos, newState, moved);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                              InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            TerminalFilter.prepareCreativeGroups(serverPlayer);
        }
        StorageCoreBlockEntity core = StorageNetwork.findCore(world, pos);
        if (core == null) {
            player.displayClientMessage(Component.translatable("message.tristorage.no_core"), true);
            return InteractionResult.CONSUME;
        }
        if (core.isRecoveryRequired()) {
            player.displayClientMessage(Component.translatable(
                    "message.tristorage.core_recovery_required"), true);
            return InteractionResult.CONSUME;
        }
        if (!core.runtime().isReady()) {
            player.displayClientMessage(Component.translatable(
                    "message.tristorage.core_not_ready"), true);
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider(
                (syncId, inventory, ignored) -> new TerminalScreenHandler(syncId, inventory, core)
                        .accessedFrom(pos),
                Component.translatable("screen.tristorage.terminal")
        ));
        return InteractionResult.CONSUME;
    }
}
