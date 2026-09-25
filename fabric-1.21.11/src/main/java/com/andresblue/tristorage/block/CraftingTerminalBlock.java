package com.andresblue.tristorage.block;

import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class CraftingTerminalBlock extends Block implements NetworkBlock {
    public CraftingTerminalBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                 BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        StorageCoreBlockEntity core = StorageNetwork.findCore(world, pos);
        if (core == null) {
            player.sendMessage(Text.translatable("message.tristorage.no_core"), true);
            return ActionResult.CONSUME;
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) ->
                        new CraftingTerminalScreenHandler(syncId, inventory, core),
                Text.translatable("screen.tristorage.crafting_terminal")
        ));
        return ActionResult.CONSUME;
    }
}
