package com.andresblue.tristorage.item;

import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.storage.RemoteAccessManager;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class RemoteTabletItem extends Item {
    private static final String DIMENSION_KEY = "LinkedDimension";
    private static final String POSITION_KEY = "LinkedPosition";
    private static final String CORE_POSITION_KEY = "LinkedCorePosition";

    public RemoteTabletItem(Settings settings) {
        super(settings);
    }

    public ActionResult linkTo(World world, BlockPos linkerPos, PlayerEntity player, ItemStack tablet) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        StorageCoreBlockEntity core = StorageNetwork.findCore(world, linkerPos);
        if (core == null) {
            player.sendMessage(Text.translatable("message.tristorage.linker_offline"), true);
            return ActionResult.CONSUME;
        }
        if (!world.getRegistryKey().equals(World.OVERWORLD)
                && (!(world.getBlockEntity(linkerPos) instanceof LinkerBlockEntity linker)
                || !linker.isAntennaActive())) {
            player.sendMessage(Text.translatable("message.tristorage.remote_requires_antenna"), true);
            return ActionResult.CONSUME;
        }
        NbtCompound nbt = customData(tablet);
        nbt.putString(DIMENSION_KEY, world.getRegistryKey().getValue().toString());
        nbt.putLong(POSITION_KEY, linkerPos.asLong());
        nbt.putLong(CORE_POSITION_KEY, core.getPos().asLong());
        tablet.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        player.sendMessage(Text.translatable("message.tristorage.tablet_linked"), true);
        return ActionResult.CONSUME;
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        ItemStack tablet = player.getStackInHand(hand);
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        NbtCompound nbt = customData(tablet);
        if (!nbt.contains(DIMENSION_KEY) || !nbt.contains(POSITION_KEY)) {
            player.sendMessage(Text.translatable("message.tristorage.tablet_unlinked"), true);
            return ActionResult.FAIL;
        }

        Identifier dimensionId = Identifier.tryParse(nbt.getString(DIMENSION_KEY, ""));
        if (dimensionId == null || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.FAIL;
        }
        RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, dimensionId);
        ServerWorld targetWorld = serverPlayer.getEntityWorld().getServer().getWorld(dimension);
        BlockPos linkerPos = BlockPos.fromLong(nbt.getLong(POSITION_KEY, 0L));
        if (targetWorld == null) {
            player.sendMessage(Text.translatable("message.tristorage.remote_unavailable"), true);
            return ActionResult.FAIL;
        }

        BlockPos coreHint = nbt.contains(CORE_POSITION_KEY)
                ? BlockPos.fromLong(nbt.getLong(CORE_POSITION_KEY, 0L)) : null;
        RemoteAccessManager.request(serverPlayer, hand, targetWorld, linkerPos, coreHint);
        return ActionResult.CONSUME;
    }

    public static boolean isLinkedTo(ItemStack stack, RegistryKey<World> dimension,
                                     BlockPos linkerPos) {
        if (!(stack.getItem() instanceof RemoteTabletItem)) {
            return false;
        }
        NbtCompound nbt = customData(stack);
        return nbt.contains(DIMENSION_KEY)
                && nbt.contains(POSITION_KEY)
                && dimension.getValue().toString().equals(nbt.getString(DIMENSION_KEY, ""))
                && linkerPos.asLong() == nbt.getLong(POSITION_KEY, 0L);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        NbtCompound nbt = customData(stack);
        return nbt.contains(DIMENSION_KEY) && nbt.contains(POSITION_KEY);
    }

    private static NbtCompound customData(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }
}
