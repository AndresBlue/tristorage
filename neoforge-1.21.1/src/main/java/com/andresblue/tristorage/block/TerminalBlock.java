package com.andresblue.tristorage.block;

import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class TerminalBlock extends Block implements NetworkBlock {
    public TerminalBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        StorageCoreBlockEntity core = StorageNetwork.findCore(level, pos);
        if (core == null) {
            player.displayClientMessage(Component.translatable("message.tristorage.no_core"), true);
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new TerminalScreenHandler(id, inventory, core),
                Component.translatable("screen.tristorage.terminal")));
        return InteractionResult.CONSUME;
    }
}
