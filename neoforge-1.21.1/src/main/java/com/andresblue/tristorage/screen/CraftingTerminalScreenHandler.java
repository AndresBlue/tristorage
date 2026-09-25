package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Optional;

public final class CraftingTerminalScreenHandler extends TerminalScreenHandler {
    public static final int CRAFT_INPUT_START = PLAYER_END;
    public static final int CRAFT_INPUT_END = CRAFT_INPUT_START + 9;
    public static final int RESULT_SLOT = CRAFT_INPUT_END;

    private final CraftingContainer craftingInput;
    private final ResultContainer craftingResult;
    private final Player player;

    public CraftingTerminalScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    public CraftingTerminalScreenHandler(int syncId, Inventory playerInventory,
                                         StorageCoreBlockEntity core) {
        super(TriStorageMod.CRAFTING_TERMINAL_SCREEN_HANDLER.get(), syncId,
                playerInventory, core, null);
        this.player = playerInventory.player;
        this.craftingInput = new TransientCraftingContainer(this, 3, 3);
        this.craftingResult = new ResultContainer();

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(craftingInput, column + row * 3,
                        184 + column * 18, 20 + row * 18));
            }
        }
        addSlot(new ResultSlot(player, craftingInput, craftingResult,
                0, 272, 38));
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (inventory == craftingInput) {
            updateCraftingResult();
        }
    }

    private void updateCraftingResult() {
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack output = ItemStack.EMPTY;
        var craftInput = craftingInput.asCraftInput();
        Optional<RecipeHolder<CraftingRecipe>> match = serverPlayer.server
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftInput, player.level());
        if (match.isPresent()) {
            RecipeHolder<CraftingRecipe> recipe = match.get();
            if (craftingResult.setRecipeUsed(player.level(), serverPlayer, recipe)) {
                ItemStack crafted = recipe.value().assemble(craftInput,
                        player.level().registryAccess());
                if (crafted.isItemEnabled(player.level().enabledFeatures())) {
                    output = crafted;
                }
            }
        }

        craftingResult.setItem(0, output);
        setRemoteSlot(RESULT_SLOT, output);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                containerId, incrementStateId(), RESULT_SLOT, output));
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType actionType,
                        Player player) {
        if (core != null && slotIndex == RESULT_SLOT
                && actionType == ClickType.QUICK_MOVE) {
            ItemStack expected = slots.get(RESULT_SLOT).getItem().copy();
            for (int crafts = 0; crafts < 4096 && !expected.isEmpty(); crafts++) {
                ItemStack current = slots.get(RESULT_SLOT).getItem();
                if (current.isEmpty() || !ItemStack.isSameItemSameComponents(expected, current)
                        || quickMoveStack(player, RESULT_SLOT).isEmpty()) {
                    break;
                }
            }
            refreshPage();
            return;
        }
        if (core != null && slotIndex >= CRAFT_INPUT_START
                && slotIndex < CRAFT_INPUT_END
                && actionType == ClickType.PICKUP_ALL) {
            collectMatchingRealStacks(player);
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
    }

    private void collectMatchingRealStacks(Player player) {
        ItemStack cursor = getCarried();
        if (cursor.isEmpty()) {
            return;
        }
        int[] starts = {CRAFT_INPUT_START, PLAYER_START};
        int[] ends = {CRAFT_INPUT_END, PLAYER_END};
        for (int pass = 0; pass < 2 && cursor.getCount() < cursor.getMaxStackSize(); pass++) {
            for (int range = 0; range < starts.length; range++) {
                for (int index = starts[range]; index < ends[range]
                        && cursor.getCount() < cursor.getMaxStackSize(); index++) {
                    Slot slot = slots.get(index);
                    ItemStack candidate = slot.getItem();
                    if (candidate.isEmpty() || !ItemStack.isSameItemSameComponents(cursor, candidate)
                            || !slot.mayPickup(player)
                            || (pass == 0 && candidate.getCount() == candidate.getMaxStackSize())) {
                        continue;
                    }
                    int amount = Math.min(candidate.getCount(),
                            cursor.getMaxStackSize() - cursor.getCount());
                    ItemStack taken = slot.remove(amount);
                    cursor.grow(taken.getCount());
                    slot.onTake(player, taken);
                }
            }
        }
        setCarried(cursor);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        if (slotIndex < CRAFT_INPUT_START) {
            return super.quickMoveStack(player, slotIndex);
        }

        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();

        if (slotIndex == RESULT_SLOT) {
            source.getItem().onCraftedBy(source, player.level(), player);
            if (!moveItemStackTo(source, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(source, original);
        } else {
            boolean moved = false;
            if (coreIsAttached()) {
                long inserted = core.insert(source, source.getCount());
                if (inserted > 0) {
                    source.shrink((int) inserted);
                    moved = true;
                    refreshPage();
                }
            }
            if (!source.isEmpty()
                    && moveItemStackTo(source, PLAYER_START, PLAYER_END, false)) {
                moved = true;
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (source.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, source);
        if (slotIndex == RESULT_SLOT && !source.isEmpty()) {
            player.drop(source, false);
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        returnCraftingInput(player);
        craftingResult.clearContent();
        super.removed(player);
    }

    private void returnCraftingInput(Player player) {
        Runnable returnItems = () -> {
            for (int slot = 0; slot < craftingInput.getContainerSize(); slot++) {
                ItemStack remaining = craftingInput.removeItemNoUpdate(slot);
                if (remaining.isEmpty()) {
                    continue;
                }
                if (coreIsAttached()) {
                    long inserted = core.insert(remaining, remaining.getCount());
                    remaining.shrink((int) inserted);
                }
                if (!remaining.isEmpty() && player.isAlive()) {
                    player.getInventory().add(remaining);
                }
                if (!remaining.isEmpty()) {
                    player.drop(remaining, false);
                }
            }
        };
        if (coreIsAttached()) {
            core.batchMutations(returnItems);
        } else {
            returnItems.run();
        }
    }

    private boolean coreIsAttached() {
        return core != null && !core.isRemoved() && core.getLevel() != null
                && core.getLevel().getBlockEntity(core.getBlockPos()) == core;
    }
}
