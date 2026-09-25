package com.andresblue.tristorage.blockentity;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.block.AntennaMount;
import com.andresblue.tristorage.block.LinkerBlock;
import com.andresblue.tristorage.screen.LinkerScreenHandler;
import com.andresblue.tristorage.storage.StorageNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class LinkerBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
    private static final String PREVIEW_KEY = "OrbitPreview";
    private static final int PREVIEW_SIZE = 18;
    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(1, ItemStack.EMPTY);
    private final List<ItemStack> orbitPreview = new ArrayList<>();
    private int previewTicker;
    private int observedCoreRevision = Integer.MIN_VALUE;

    public LinkerBlockEntity(BlockPos pos, BlockState state) {
        super(TriStorageMod.LINKER_BLOCK_ENTITY, pos, state);
    }

    public boolean hasDimensionalAntenna() {
        return stacks.getFirst().isOf(TriStorageMod.DIMENSIONAL_ANTENNA);
    }

    public boolean isAntennaActive() {
        return hasDimensionalAntenna() && antennaMount() != AntennaMount.NONE;
    }

    public AntennaMount antennaMount() {
        BlockState state = getCachedState();
        return state.contains(LinkerBlock.ANTENNA)
                ? state.get(LinkerBlock.ANTENNA) : AntennaMount.NONE;
    }

    public boolean canInstallAntenna() {
        return world != null && LinkerBlock.findAntennaMount(world, pos) != AntennaMount.NONE;
    }

    public void refreshAntennaMount() {
        if (world == null || world.isClient() || !(getCachedState().getBlock() instanceof LinkerBlock)) {
            return;
        }
        AntennaMount wanted = hasDimensionalAntenna()
                ? LinkerBlock.findAntennaMount(world, pos) : AntennaMount.NONE;
        if (antennaMount() != wanted) {
            world.setBlockState(pos, getCachedState().with(LinkerBlock.ANTENNA, wanted),
                    Block.NOTIFY_LISTENERS);
        }
    }

    public List<ItemStack> orbitPreview() {
        return List.copyOf(orbitPreview);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, LinkerBlockEntity linker) {
        if (++linker.previewTicker < 20) {
            return;
        }
        linker.previewTicker = 0;
        linker.refreshAntennaMount();
        if (!linker.isAntennaActive()) {
            if (!linker.orbitPreview.isEmpty()) {
                linker.orbitPreview.clear();
                linker.observedCoreRevision = Integer.MIN_VALUE;
                linker.syncPreview();
            }
            return;
        }
        var core = StorageNetwork.findCore(world, pos);
        if (core == null) {
            if (!linker.orbitPreview.isEmpty()) {
                linker.orbitPreview.clear();
                linker.observedCoreRevision = Integer.MIN_VALUE;
                linker.syncPreview();
            }
            return;
        }
        if (linker.observedCoreRevision == core.orbitSnapshotRevision()) {
            return;
        }
        linker.observedCoreRevision = core.orbitSnapshotRevision();
        List<ItemStack> replacement = core.orbitItemSnapshot().stream()
                .limit(PREVIEW_SIZE)
                .map(stack -> stack.copyWithCount(1))
                .toList();
        if (!samePreview(linker.orbitPreview, replacement)) {
            linker.orbitPreview.clear();
            linker.orbitPreview.addAll(replacement);
            linker.syncPreview();
        }
    }

    private static boolean samePreview(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (!ItemStack.areItemsAndComponentsEqual(first.get(index), second.get(index))) {
                return false;
            }
        }
        return true;
    }

    private void syncPreview() {
        markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return stacks.getFirst().isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return stacks.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(stacks, slot, amount);
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack removed = Inventories.removeStack(stacks, slot);
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        ItemStack safe = stack.isOf(TriStorageMod.DIMENSIONAL_ANTENNA)
                ? stack.copyWithCount(1) : ItemStack.EMPTY;
        stacks.set(slot, safe);
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == 0 && stack.isOf(TriStorageMod.DIMENSIONAL_ANTENNA)
                && !hasDimensionalAntenna() && canInstallAntenna();
    }

    @Override
    public void clear() {
        stacks.clear();
        markDirty();
    }

    @Override
    public void markDirty() {
        super.markDirty();
        refreshAntennaMount();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        Inventories.writeData(view, stacks);
        WriteView.ListView preview = view.getList(PREVIEW_KEY);
        for (ItemStack stack : orbitPreview) {
            preview.add().put("Stack", ItemStack.CODEC, stack);
        }
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        stacks.clear();
        Inventories.readData(view, stacks);
        orbitPreview.clear();
        for (ReadView entry : view.getListReadView(PREVIEW_KEY)) {
            ItemStack stack = entry.read("Stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty() && orbitPreview.size() < PREVIEW_SIZE) {
                orbitPreview.add(stack.copyWithCount(1));
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    protected void addComponents(ComponentMap.Builder builder) {
        super.addComponents(builder);
        if (!isEmpty()) {
            builder.add(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks));
        }
    }

    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        components.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(stacks);
    }

    @Override
    public void removeFromCopiedStackData(WriteView view) {
        super.removeFromCopiedStackData(view);
        view.remove(Inventories.ITEMS_NBT_KEY);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("screen.tristorage.linker");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new LinkerScreenHandler(syncId, playerInventory, this);
    }
}
