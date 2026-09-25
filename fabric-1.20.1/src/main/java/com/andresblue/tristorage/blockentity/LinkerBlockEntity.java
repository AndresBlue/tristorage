package com.andresblue.tristorage.blockentity;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.AntennaMount;
import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.screen.LinkerScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class LinkerBlockEntity extends BlockEntity
        implements Inventory, NamedScreenHandlerFactory {
    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(1, ItemStack.EMPTY);

    public LinkerBlockEntity(BlockPos pos, BlockState state) {
        super(TriStorageMod.LINKER_BLOCK_ENTITY, pos, state);
    }

    public boolean hasAntenna() {
        return stacks.get(0).isOf(TriStorageMod.DIMENSIONAL_ANTENNA);
    }

    public boolean isAntennaActive() {
        return hasAntenna() && getCachedState().get(LinkerBlock.ANTENNA) != AntennaMount.NONE;
    }

    public AntennaMount antennaMount() {
        return getCachedState().get(LinkerBlock.ANTENNA);
    }

    public boolean canInstallAntenna() {
        return world != null && LinkerBlock.findAntennaMount(world, pos) != AntennaMount.NONE;
    }

    public void refreshAntennaMount() {
        if (world == null || world.isClient || !(getCachedState().getBlock() instanceof LinkerBlock)) {
            return;
        }
        AntennaMount wanted = hasAntenna()
                ? LinkerBlock.findAntennaMount(world, pos)
                : AntennaMount.NONE;
        if (antennaMount() != wanted) {
            world.setBlockState(pos, getCachedState().with(LinkerBlock.ANTENNA, wanted),
                    LinkerBlock.NOTIFY_LISTENERS);
        }
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return stacks.get(0).isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot == 0 ? stacks.get(0) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(stacks, slot, amount);
        if (!removed.isEmpty()) {
            inventoryChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack removed = Inventories.removeStack(stacks, slot);
        if (!removed.isEmpty()) {
            inventoryChanged();
        }
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot != 0) {
            return;
        }
        ItemStack safe = stack.isOf(TriStorageMod.DIMENSIONAL_ANTENNA)
                ? stack.copy() : ItemStack.EMPTY;
        safe.setCount(Math.min(1, safe.getCount()));
        stacks.set(0, safe);
        inventoryChanged();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return world != null && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == 0 && stack.isOf(TriStorageMod.DIMENSIONAL_ANTENNA)
                && !hasAntenna() && canInstallAntenna();
    }

    @Override
    public void clear() {
        stacks.clear();
        inventoryChanged();
    }

    private void inventoryChanged() {
        markDirty();
        refreshAntennaMount();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, stacks);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        stacks.clear();
        Inventories.readNbt(nbt, stacks);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("screen.tristorage.linker");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory,
                                    PlayerEntity player) {
        return new LinkerScreenHandler(syncId, playerInventory, this);
    }
}
