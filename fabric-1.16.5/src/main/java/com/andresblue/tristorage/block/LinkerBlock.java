package com.andresblue.tristorage.block;

import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class LinkerBlock extends Block implements NetworkBlock {
    public LinkerBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        if (held.getItem() instanceof RemoteTabletItem) {
            return ((RemoteTabletItem) held.getItem()).linkTo(world, pos, player, held);
        }
        if (!world.isClient) {
            player.sendMessage(new TranslatableText(
                    StorageNetwork.findCore(world, pos) == null
                            ? "message.tristorage.linker_offline"
                            : "message.tristorage.linker_online"
            ), true);
        }
        return ActionResult.SUCCESS;
    }
}
