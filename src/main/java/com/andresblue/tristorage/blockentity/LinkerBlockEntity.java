package com.andresblue.tristorage.blockentity;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.AntennaMount;
import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.screen.LinkerScreenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class LinkerBlockEntity extends BlockEntity
        implements Container, MenuProvider {
    private final NonNullList<ItemStack> stacks = NonNullList.withSize(1, ItemStack.EMPTY);

    public LinkerBlockEntity(BlockPos pos, BlockState state) {
        super(TriStorageMod.LINKER_BLOCK_ENTITY, pos, state);
    }

    public boolean hasAntenna() {
        return stacks.get(0).is(TriStorageMod.DIMENSIONAL_ANTENNA);
    }

    public boolean isAntennaActive() {
        return hasAntenna() && getBlockState().getValue(LinkerBlock.ANTENNA) != AntennaMount.NONE;
    }

    public AntennaMount antennaMount() {
        return getBlockState().getValue(LinkerBlock.ANTENNA);
    }

    public boolean canInstallAntenna() {
        return level != null && LinkerBlock.findAntennaMount(level, worldPosition) != AntennaMount.NONE;
    }

    public void refreshAntennaMount() {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof LinkerBlock)) {
            return;
        }
        AntennaMount wanted = hasAntenna()
                ? LinkerBlock.findAntennaMount(level, worldPosition)
                : AntennaMount.NONE;
        if (antennaMount() != wanted) {
            level.setBlock(worldPosition, getBlockState().setValue(LinkerBlock.ANTENNA, wanted),
                    LinkerBlock.UPDATE_CLIENTS);
        }
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return stacks.get(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? stacks.get(0) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(stacks, slot, amount);
        if (!removed.isEmpty()) {
            inventoryChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(stacks, slot);
        if (!removed.isEmpty()) {
            inventoryChanged();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }
        ItemStack safe = stack.is(TriStorageMod.DIMENSIONAL_ANTENNA)
                ? stack.copy() : ItemStack.EMPTY;
        safe.setCount(Math.min(1, safe.getCount()));
        stacks.set(0, safe);
        inventoryChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && stack.is(TriStorageMod.DIMENSIONAL_ANTENNA)
                && !hasAntenna() && canInstallAntenna();
    }

    @Override
    public void clearContent() {
        stacks.clear();
        inventoryChanged();
    }

    private void inventoryChanged() {
        setChanged();
        refreshAntennaMount();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        ContainerHelper.saveAllItems(nbt, stacks);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        stacks.clear();
        ContainerHelper.loadAllItems(nbt, stacks);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.tristorage.linker");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory,
                                    Player player) {
        return new LinkerScreenHandler(syncId, playerInventory, this);
    }
}
