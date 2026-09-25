package com.andresblue.tristorage.block;

import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
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

public final class CraftingTerminalBlock extends Block implements NetworkBlock {
    public CraftingTerminalBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        StorageCoreBlockEntity core = StorageNetwork.findCore(level, pos);
        if (core == null) {
            player.displayClientMessage(Component.translatable("message.tristorage.no_core"), true);
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new CraftingTerminalScreenHandler(id, inventory, core),
                Component.translatable("screen.tristorage.crafting_terminal")));
        return InteractionResult.CONSUME;
    }
}
