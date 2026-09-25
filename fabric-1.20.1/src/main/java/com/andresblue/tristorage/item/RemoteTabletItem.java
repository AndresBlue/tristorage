package com.andresblue.tristorage.item;

import com.andresblue.tristorage.storage.RemoteAccessManager;
import com.andresblue.tristorage.storage.RemoteTerminalMode;
import com.andresblue.tristorage.storage.StorageNetwork;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.blockentity.LinkerBlockEntity;
import com.andresblue.tristorage.block.LinkerBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class RemoteTabletItem extends Item {
    private static final String DIMENSION_KEY = "LinkedDimension";
    private static final String POSITION_KEY = "LinkedPosition";
    private static final String CORE_POSITION_KEY = "LinkedCorePosition";

    private final RemoteTerminalMode mode;

    public RemoteTabletItem(Settings settings, RemoteTerminalMode mode) {
        super(settings);
        this.mode = mode;
    }

    public RemoteTerminalMode mode() {
        return mode;
    }

    public ActionResult linkTo(World world, BlockPos linkerPos, PlayerEntity player, ItemStack tablet) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        StorageCoreBlockEntity core = StorageNetwork.findCore(world, linkerPos);
        if (core == null) {
            player.sendMessage(Text.translatable("message.tristorage.linker_offline"), true);
            return ActionResult.CONSUME;
        }
        LinkerBlockEntity linker = LinkerBlock.getOrCreateLinkerEntity(
                world, linkerPos, world.getBlockState(linkerPos));
        if (!world.getRegistryKey().equals(World.OVERWORLD)
                && (linker == null
                || !linker.isAntennaActive())) {
            player.sendMessage(Text.translatable(
                    "message.tristorage.remote_requires_antenna"), true);
            return ActionResult.CONSUME;
        }
        NbtCompound nbt = tablet.getOrCreateNbt();
        nbt.putString(DIMENSION_KEY, world.getRegistryKey().getValue().toString());
        nbt.putLong(POSITION_KEY, linkerPos.asLong());
        nbt.putLong(CORE_POSITION_KEY, core.getPos().asLong());
        player.sendMessage(Text.translatable("message.tristorage.tablet_linked"), true);
        return ActionResult.CONSUME;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack tablet = player.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(tablet);
        }
        NbtCompound nbt = tablet.getNbt();
        if (nbt == null || !nbt.contains(DIMENSION_KEY) || !nbt.contains(POSITION_KEY)) {
            player.sendMessage(Text.translatable("message.tristorage.tablet_unlinked"), true);
            return TypedActionResult.fail(tablet);
        }

        Identifier dimensionId = Identifier.tryParse(nbt.getString(DIMENSION_KEY));
        if (dimensionId == null || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.fail(tablet);
        }
        RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, dimensionId);
        ServerWorld targetWorld = serverPlayer.getServer().getWorld(dimension);
        BlockPos linkerPos = BlockPos.fromLong(nbt.getLong(POSITION_KEY));
        if (targetWorld == null) {
            player.sendMessage(Text.translatable("message.tristorage.remote_unavailable"), true);
            return TypedActionResult.fail(tablet);
        }

        BlockPos coreHint = nbt.contains(CORE_POSITION_KEY)
                ? BlockPos.fromLong(nbt.getLong(CORE_POSITION_KEY))
                : null;
        RemoteAccessManager.request(serverPlayer, hand, targetWorld, linkerPos, coreHint, mode);
        return TypedActionResult.consume(tablet);
    }

    public static boolean isLinkedTo(ItemStack stack, RegistryKey<World> dimension,
                                     BlockPos linkerPos) {
        return isLinkedTo(stack, dimension, linkerPos, null);
    }

    public static boolean isLinkedTo(ItemStack stack, RegistryKey<World> dimension,
                                     BlockPos linkerPos, RemoteTerminalMode requiredMode) {
        if (!(stack.getItem() instanceof RemoteTabletItem)) {
            return false;
        }
        RemoteTabletItem tablet = (RemoteTabletItem) stack.getItem();
        if (requiredMode != null && tablet.mode != requiredMode) {
            return false;
        }
        NbtCompound nbt = stack.getNbt();
        return nbt != null
                && hasValidLink(nbt)
                && dimension.getValue().toString().equals(nbt.getString(DIMENSION_KEY))
                && linkerPos.asLong() == nbt.getLong(POSITION_KEY);
    }

    /** Copies only a structurally valid TriStorage link, never arbitrary item NBT. */
    public static boolean copyValidatedLink(ItemStack source, ItemStack target) {
        NbtCompound sourceNbt = source.getNbt();
        if (sourceNbt == null || !hasValidLink(sourceNbt)) {
            return false;
        }
        NbtCompound targetNbt = target.getOrCreateNbt();
        targetNbt.putString(DIMENSION_KEY, sourceNbt.getString(DIMENSION_KEY));
        targetNbt.putLong(POSITION_KEY, sourceNbt.getLong(POSITION_KEY));
        if (sourceNbt.contains(CORE_POSITION_KEY, NbtElement.LONG_TYPE)) {
            targetNbt.putLong(CORE_POSITION_KEY, sourceNbt.getLong(CORE_POSITION_KEY));
        }
        return true;
    }

    static boolean hasValidLink(NbtCompound nbt) {
        return nbt.contains(DIMENSION_KEY, NbtElement.STRING_TYPE)
                && Identifier.tryParse(nbt.getString(DIMENSION_KEY)) != null
                && nbt.contains(POSITION_KEY, NbtElement.LONG_TYPE)
                && (!nbt.contains(CORE_POSITION_KEY)
                || nbt.contains(CORE_POSITION_KEY, NbtElement.LONG_TYPE));
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && hasValidLink(nbt);
    }
}
