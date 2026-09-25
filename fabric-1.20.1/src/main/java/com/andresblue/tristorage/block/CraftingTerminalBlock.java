package com.andresblue.tristorage.block;

import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.storage.StorageNetwork;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class CraftingTerminalBlock extends Block implements NetworkBlock {
    public CraftingTerminalBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        StorageNetwork.markTopologyChanged(world);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            StorageNetwork.markTopologyChanged(world);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            TerminalFilter.prepareCreativeGroups(serverPlayer);
        }
        StorageCoreBlockEntity core = StorageNetwork.findCore(world, pos);
        if (core == null) {
            player.sendMessage(Text.translatable("message.tristorage.no_core"), true);
            return ActionResult.CONSUME;
        }
        if (core.isRecoveryRequired()) {
            player.sendMessage(Text.translatable(
                    "message.tristorage.core_recovery_required"), true);
            return ActionResult.CONSUME;
        }
        if (!core.runtime().isReady()) {
            player.sendMessage(Text.translatable(
                    "message.tristorage.core_not_ready"), true);
            return ActionResult.CONSUME;
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) ->
                        new CraftingTerminalScreenHandler(syncId, inventory, core)
                                .accessedFrom(pos),
                Text.translatable("screen.tristorage.crafting_terminal")
        ));
        return ActionResult.CONSUME;
    }
}
