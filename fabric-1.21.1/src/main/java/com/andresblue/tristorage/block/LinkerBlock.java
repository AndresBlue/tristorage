package com.andresblue.tristorage.block;

import com.andresblue.tristorage.item.RemoteTabletItem;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class LinkerBlock extends Block implements NetworkBlock {
    public LinkerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack held, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (held.getItem() instanceof RemoteTabletItem tablet) {
            return tablet.linkTo(world, pos, player, held);
        }
        if (!world.isClient()) {
            player.sendMessage(Text.translatable(
                    StorageNetwork.findCore(world, pos) == null
                            ? "message.tristorage.linker_offline"
                            : "message.tristorage.linker_online"
            ), true);
        }
        return ItemActionResult.SUCCESS;
    }
}
