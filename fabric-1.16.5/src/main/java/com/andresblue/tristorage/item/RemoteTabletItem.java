package com.andresblue.tristorage.item;

import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.RemoteChunkLease;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.OptionalInt;

public final class RemoteTabletItem extends Item {
    private static final String DIMENSION_KEY = "LinkedDimension";
    private static final String POSITION_KEY = "LinkedPosition";

    public RemoteTabletItem(Settings settings) {
        super(settings);
    }

    public ActionResult linkTo(World world, BlockPos linkerPos, PlayerEntity player, ItemStack tablet) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (StorageNetwork.findCore(world, linkerPos) == null) {
            player.sendMessage(new TranslatableText("message.tristorage.linker_offline"), true);
            return ActionResult.CONSUME;
        }
        NbtCompound nbt = tablet.getOrCreateTag();
        nbt.putString(DIMENSION_KEY, world.getRegistryKey().getValue().toString());
        nbt.putLong(POSITION_KEY, linkerPos.asLong());
        player.sendMessage(new TranslatableText("message.tristorage.tablet_linked"), true);
        return ActionResult.CONSUME;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack tablet = player.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(tablet);
        }
        NbtCompound nbt = tablet.getTag();
        if (nbt == null || !nbt.contains(DIMENSION_KEY) || !nbt.contains(POSITION_KEY)) {
            player.sendMessage(new TranslatableText("message.tristorage.tablet_unlinked"), true);
            return TypedActionResult.fail(tablet);
        }

        Identifier dimensionId = Identifier.tryParse(nbt.getString(DIMENSION_KEY));
        if (dimensionId == null || !(player instanceof ServerPlayerEntity)) {
            return TypedActionResult.fail(tablet);
        }
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        RegistryKey<World> dimension = RegistryKey.of(Registry.WORLD_KEY, dimensionId);
        ServerWorld targetWorld = serverPlayer.getServer().getWorld(dimension);
        BlockPos linkerPos = BlockPos.fromLong(nbt.getLong(POSITION_KEY));
        if (targetWorld == null) {
            player.sendMessage(new TranslatableText("message.tristorage.remote_unavailable"), true);
            return TypedActionResult.fail(tablet);
        }

        RemoteChunkLease lease = RemoteChunkLease.acquire(targetWorld, linkerPos);
        if (!(targetWorld.getBlockState(linkerPos).getBlock() instanceof LinkerBlock)) {
            lease.release();
            player.sendMessage(new TranslatableText("message.tristorage.remote_unavailable"), true);
            return TypedActionResult.fail(tablet);
        }
        StorageCoreBlockEntity core = StorageNetwork.findCore(targetWorld, linkerPos);
        if (core == null) {
            lease.release();
            player.sendMessage(new TranslatableText("message.tristorage.linker_offline"), true);
            return TypedActionResult.fail(tablet);
        }
        lease.include(core.getPos());
        OptionalInt opened = serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, ignored) ->
                        new TerminalScreenHandler(syncId, inventory, core, lease),
                new TranslatableText("screen.tristorage.remote_terminal")
        ));
        if (!opened.isPresent()) {
            lease.release();
            return TypedActionResult.fail(tablet);
        }
        return TypedActionResult.consume(tablet);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        NbtCompound nbt = stack.getTag();
        return nbt != null && nbt.contains(DIMENSION_KEY) && nbt.contains(POSITION_KEY);
    }
}
