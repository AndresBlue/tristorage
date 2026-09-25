package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.ItemKey;
import com.andresblue.tristorage.storage.RemoteAccessHandle;
import com.andresblue.tristorage.storage.StorageMetrics;
import com.andresblue.tristorage.storage.StorageRuntime;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CraftingTerminalScreenHandler extends TerminalScreenHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "TriStorage/RecipeTransfer");
    public static final int CRAFT_INPUT_START = PLAYER_END;
    public static final int CRAFT_INPUT_END = CRAFT_INPUT_START + 9;
    public static final int RESULT_SLOT = CRAFT_INPUT_END;

    private final CraftingInventory craftingInput;
    private final CraftingResultInventory craftingResult;
    private final PlayerEntity player;
    private boolean recipeTransferInProgress;
    private long nextRecipeTransferTick;
    private long nextRecipeAvailabilityTick;
    private RecipeAvailabilityKey cachedAvailabilityKey;
    private RecipeTransferPlanner.Availability cachedAvailability;

    public CraftingTerminalScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    public CraftingTerminalScreenHandler(int syncId, PlayerInventory playerInventory,
                                         StorageCoreBlockEntity core) {
        this(syncId, playerInventory, core, null);
    }

    public CraftingTerminalScreenHandler(int syncId, PlayerInventory playerInventory,
                                         StorageCoreBlockEntity core,
                                         RemoteAccessHandle remoteAccessHandle) {
        super(TriStorageMod.CRAFTING_TERMINAL_SCREEN_HANDLER, syncId,
                playerInventory, core, remoteAccessHandle);
        this.player = playerInventory.player;
        this.craftingInput = new CraftingInventory(this, 3, 3);
        this.craftingResult = new CraftingResultInventory();

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(craftingInput, column + row * 3,
                        184 + column * 18, 20 + row * 18));
            }
        }
        addSlot(new AutoRefillingResultSlot(player, craftingInput, craftingResult,
                0, 272, 38));
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        if (inventory == craftingInput && !recipeTransferInProgress) {
            updateCraftingResult();
        }
    }

    /**
     * Fills the crafting grid from the complete storage, not merely the 54
     * virtual entries currently projected into the terminal screen. JEI and
     * EMI call this through a small server-authoritative recipe-id packet.
     */
    public boolean fillRecipe(ServerPlayerEntity requester, CraftingRecipe recipe,
                              int requestedCrafts) {
        if (requester != player || requester.currentScreenHandler != this
                || !coreIsAttached() || !canUse(requester)
                || recipe == null || !recipe.fits(3, 3)
                || recipe.getIngredients().stream().allMatch(ingredient -> ingredient.isEmpty())) {
            requester.sendMessage(Text.translatable(
                    "message.tristorage.recipe_transfer_unsupported"), true);
            return false;
        }
        long serverTick = requester.getServerWorld().getTime();
        if (serverTick < nextRecipeTransferTick) {
            return false;
        }
        // Recipe viewers can emit many clicks before their first packet round
        // trip completes. Bound expensive planning/extraction work even for a
        // modified client; the normal client additionally permits one request
        // in flight at a time.
        nextRecipeTransferTick = serverTick + 2L;

        TransferResources available = collectTransferResources(recipe, false);
        RecipeTransferPlanner.Plan plan = RecipeTransferPlanner.plan(
                recipe, available.resources, requestedCrafts);
        if (plan == null) {
            // The indexed path is the fast path. A single bounded full-catalog
            // pass is a correctness fallback for unusual ingredients and old
            // hot runtimes whose item index was not populated.
            available = collectTransferResources(recipe, true);
            plan = RecipeTransferPlanner.plan(recipe, available.resources,
                    requestedCrafts);
        }
        if (plan == null) {
            LOGGER.warn("Could not plan recipe transfer for {} with {} candidate variants "
                            + "in storage {} (requested={})",
                    recipe.getId(), available.storageEntries.size(),
                    core.storageId(), requestedCrafts);
            requester.sendMessage(Text.translatable(
                    "message.tristorage.recipe_transfer_missing"), true);
            return false;
        }

        RecipeTransferPlanner.Plan finalPlan = plan;
        TransferResources finalAvailable = available;
        boolean success = core.batchMutations(() -> applyTransferPlan(
                requester, finalPlan, finalAvailable.storageEntries));
        if (!success) {
            LOGGER.warn("Recipe transfer extraction changed unexpectedly for {} in storage {}",
                    recipe.getId(), core.storageId());
            requester.sendMessage(Text.translatable(
                    "message.tristorage.recipe_transfer_missing"), true);
            return false;
        }
        requestStorageRefresh();
        sendContentUpdates();
        return true;
    }

    /** Real local slots exposed to optional client recipe-viewer integrations. */
    public List<ItemStack> recipeTransferClientStacks() {
        List<ItemStack> result = new ArrayList<>(45);
        for (ItemStack stack : player.getInventory().main) {
            result.add(stack.copy());
        }
        for (int slot = 0; slot < craftingInput.size(); slot++) {
            result.add(craftingInput.getStack(slot).copy());
        }
        return result;
    }

    /** Cheap client-side identity for local ingredients included in preflight. */
    public long recipeTransferClientFingerprint() {
        long hash = 0xCBF29CE484222325L;
        for (ItemStack stack : player.getInventory().main) {
            hash = fingerprintStack(hash, stack);
        }
        for (int slot = 0; slot < craftingInput.size(); slot++) {
            hash = fingerprintStack(hash, craftingInput.getStack(slot));
        }
        return fingerprintStack(hash, getCursorStack());
    }

    private static long fingerprintStack(long hash, ItemStack stack) {
        hash = (hash ^ Registries.ITEM.getRawId(stack.getItem())) * 0x100000001B3L;
        hash = (hash ^ stack.getCount()) * 0x100000001B3L;
        return (hash ^ java.util.Objects.hashCode(stack.getNbt())) * 0x100000001B3L;
    }

    /** Server-authoritative preflight used by optional recipe viewers. */
    public RecipeTransferPlanner.Availability recipeAvailability(CraftingRecipe recipe) {
        if (recipe == null || !recipe.fits(3, 3) || !coreIsAttached()) {
            return new RecipeTransferPlanner.Availability(0, 0);
        }
        RecipeAvailabilityKey key = new RecipeAvailabilityKey(recipe.getId(),
                core.stateEpoch(), recipeTransferClientFingerprint());
        if (key.equals(cachedAvailabilityKey) && cachedAvailability != null) {
            StorageMetrics.increment("recipe_availability_cache_hits");
            return cachedAvailability;
        }
        TransferResources available = collectTransferResources(recipe, false);
        RecipeTransferPlanner.Availability result = RecipeTransferPlanner.availability(
                recipe, available.resources);
        if (!result.canCraft()) {
            // The per-item index is deliberately the normal fast path, but old
            // hot runtimes and custom/dynamic ingredients are allowed to omit
            // candidates from it. Availability must have the same correctness
            // fallback as the actual transactional fill or recipe viewers can
            // claim that a valid recipe is missing ingredients.
            TransferResources complete = collectTransferResources(recipe, true);
            RecipeTransferPlanner.Availability completeResult =
                    RecipeTransferPlanner.availability(recipe, complete.resources);
            if (completeResult.canCraft()
                    || Integer.bitCount(completeResult.availableMask())
                    > Integer.bitCount(result.availableMask())) {
                result = completeResult;
            }
            StorageMetrics.increment("recipe_availability_full_fallbacks");
        }
        cachedAvailabilityKey = key;
        cachedAvailability = result;
        return result;
    }

    /** Bounds optional recipe-viewer preflight work from modified/spam clients. */
    public boolean acceptRecipeAvailabilityRequest(long clientRevision,
                                                   long localFingerprint,
                                                   long serverTick) {
        if (clientRevision != serverPageRevision()
                || localFingerprint != recipeTransferClientFingerprint()
                || serverTick < nextRecipeAvailabilityTick) {
            return false;
        }
        nextRecipeAvailabilityTick = serverTick + 1L;
        return true;
    }

    private TransferResources collectTransferResources(CraftingRecipe recipe,
                                                        boolean fullScan) {
        Map<ItemKey, StorageRuntime.SnapshotEntry> stored = new LinkedHashMap<>();
        if (fullScan) {
            for (StorageRuntime.SnapshotEntry entry : core.matchingRecipeEntries(
                    recipe.getIngredients())) {
                stored.putIfAbsent(ItemKey.frozen(entry.stack()), entry);
            }
        } else {
            for (net.minecraft.recipe.Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                for (StorageRuntime.SnapshotEntry entry : core.matchingEntries(ingredient)) {
                    stored.putIfAbsent(ItemKey.frozen(entry.stack()), entry);
                }
            }
        }

        List<RecipeTransferPlanner.Resource> resources = new ArrayList<>();
        for (StorageRuntime.SnapshotEntry entry : stored.values()) {
            resources.add(new RecipeTransferPlanner.Resource(entry.stack(), entry.count()));
        }
        for (int slot = 0; slot < craftingInput.size(); slot++) {
            ItemStack stack = craftingInput.getStack(slot);
            resources.add(new RecipeTransferPlanner.Resource(stack, stack.getCount()));
        }
        for (ItemStack stack : player.getInventory().main) {
            resources.add(new RecipeTransferPlanner.Resource(stack, stack.getCount()));
        }
        ItemStack cursor = getCursorStack();
        resources.add(new RecipeTransferPlanner.Resource(cursor, cursor.getCount()));
        return new TransferResources(resources, stored);
    }

    private boolean applyTransferPlan(ServerPlayerEntity requester,
                                      RecipeTransferPlanner.Plan plan,
                                      Map<ItemKey, StorageRuntime.SnapshotEntry> stored) {
        List<ItemStack> gridRemainders = copyStacks(craftingInput.size(),
                craftingInput::getStack);
        List<ItemStack> playerRemainders = copyStacks(
                requester.getInventory().main.size(),
                index -> requester.getInventory().main.get(index));
        ItemStack cursorRemainder = getCursorStack().copy();
        Map<ItemKey, Integer> needed = new LinkedHashMap<>(plan.consumption());

        consumeLocal(gridRemainders, needed);
        consumeLocal(playerRemainders, needed);
        consumeLocal(List.of(cursorRemainder), needed);

        List<ItemStack> extractedForRollback = new ArrayList<>();
        for (Map.Entry<ItemKey, Integer> requirement : needed.entrySet()) {
            int remaining = requirement.getValue();
            if (remaining <= 0) {
                continue;
            }
            StorageRuntime.SnapshotEntry entry = stored.get(requirement.getKey());
            while (entry != null && remaining > 0) {
                ItemStack extracted = core.extract(entry.id(), remaining);
                if (extracted.isEmpty()
                        || !requirement.getKey().equals(ItemKey.probe(extracted))) {
                    rollbackStorage(extractedForRollback);
                    return false;
                }
                extractedForRollback.add(extracted);
                remaining -= extracted.getCount();
            }
            if (remaining > 0) {
                rollbackStorage(extractedForRollback);
                return false;
            }
        }

        // No fallible operation remains below this point. Commit local source
        // decrements, replace the grid once, then return displaced leftovers.
        for (int slot = 0; slot < playerRemainders.size(); slot++) {
            requester.getInventory().main.set(slot, playerRemainders.get(slot));
        }
        setCursorStack(cursorRemainder);
        recipeTransferInProgress = true;
        try {
            for (int slot = 0; slot < craftingInput.size(); slot++) {
                craftingInput.setStack(slot, plan.grid().get(slot).copy());
            }
        } finally {
            recipeTransferInProgress = false;
        }
        returnStacks(requester, gridRemainders);
        requester.getInventory().markDirty();
        updateCraftingResult();
        return true;
    }

    private static List<ItemStack> copyStacks(int size,
                                              java.util.function.IntFunction<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(source.apply(index).copy());
        }
        return result;
    }

    private static void consumeLocal(List<ItemStack> source,
                                     Map<ItemKey, Integer> needed) {
        for (ItemStack stack : source) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.probe(stack);
            int requested = needed.getOrDefault(key, 0);
            if (requested <= 0) {
                continue;
            }
            int consumed = Math.min(stack.getCount(), requested);
            stack.decrement(consumed);
            needed.put(key, requested - consumed);
        }
    }

    private void rollbackStorage(List<ItemStack> extracted) {
        for (ItemStack stack : extracted) {
            long inserted = core.insert(stack, stack.getCount());
            if (inserted != stack.getCount()) {
                throw new IllegalStateException("TriStorage recipe transfer rollback failed");
            }
        }
    }

    private void returnStacks(PlayerEntity player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            if (coreIsAttached()) {
                long inserted = core.insert(stack, stack.getCount());
                stack.decrement((int) inserted);
            }
            if (!stack.isEmpty() && player.isAlive()) {
                player.getInventory().insertStack(stack);
            }
            if (!stack.isEmpty()) {
                player.dropItem(stack, false);
            }
        }
    }

    private void updateCraftingResult() {
        if (player.getWorld().isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        ItemStack output = ItemStack.EMPTY;
        Optional<CraftingRecipe> match = serverPlayer.getServer()
                .getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, craftingInput, player.getWorld());
        if (match.isPresent()) {
            CraftingRecipe recipe = match.get();
            if (craftingResult.shouldCraftRecipe(player.getWorld(), serverPlayer, recipe)) {
                ItemStack crafted = recipe.craft(craftingInput,
                        player.getWorld().getRegistryManager());
                if (crafted.isItemEnabled(player.getWorld().getEnabledFeatures())) {
                    output = crafted;
                }
            }
        }

        craftingResult.setStack(0, output);
        setPreviousTrackedSlot(RESULT_SLOT, output);
        serverPlayer.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
                syncId, nextRevision(), RESULT_SLOT, output));
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType,
                            PlayerEntity player) {
        if (core != null && slotIndex == RESULT_SLOT
                && actionType == SlotActionType.QUICK_MOVE) {
            ItemStack expected = slots.get(RESULT_SLOT).getStack().copy();
            int outputLimit = CraftingRefillPlanner.quickMoveOutputLimit(expected);
            int produced = 0;
            primeGridForQuickMove(expected, outputLimit);
            while (produced < outputLimit && !expected.isEmpty()) {
                ItemStack current = slots.get(RESULT_SLOT).getStack();
                int batch = current.getCount();
                if (current.isEmpty() || !ItemStack.canCombine(expected, current)
                        || batch > outputLimit - produced
                        || quickMove(player, RESULT_SLOT).isEmpty()) {
                    break;
                }
                produced += batch;
            }
            StorageMetrics.add("crafting.quick_move_items", produced);
            requestStorageRefresh();
            return;
        }
        if (core != null && slotIndex >= CRAFT_INPUT_START
                && slotIndex < CRAFT_INPUT_END
                && actionType == SlotActionType.PICKUP_ALL) {
            collectMatchingRealStacks(player);
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    /**
     * Pulls exact variants in small batches and distributes them evenly across
     * identical ingredient slots. This turns a chest stack from hundreds of
     * per-craft index mutations into only a handful while preserving vanilla
     * consumption and recipe remainder handling.
     */
    private void primeGridForQuickMove(ItemStack result, int outputLimit) {
        if (!coreIsAttached() || result.isEmpty() || outputLimit <= 0) {
            return;
        }
        int targetCrafts = Math.max(1, outputLimit / Math.max(1, result.getCount()));
        Map<ItemKey, List<Integer>> groups = new LinkedHashMap<>();
        for (int slot = 0; slot < craftingInput.size(); slot++) {
            ItemStack stack = craftingInput.getStack(slot);
            if (!stack.isEmpty()) {
                groups.computeIfAbsent(ItemKey.frozen(stack), ignored -> new ArrayList<>())
                        .add(slot);
            }
        }
        recipeTransferInProgress = true;
        try {
            for (List<Integer> group : groups.values()) {
                ItemStack template = craftingInput.getStack(group.get(0));
                int targetPerSlot = Math.min(targetCrafts, template.getMaxCount());
                int desired = 0;
                for (int slot : group) {
                    desired += Math.max(0,
                            targetPerSlot - craftingInput.getStack(slot).getCount());
                }
                int pulled = 0;
                while (pulled < desired) {
                    ItemStack extracted = core.extractMatching(
                            template, desired - pulled);
                    if (extracted.isEmpty()) {
                        break;
                    }
                    pulled += extracted.getCount();
                }
                while (pulled > 0) {
                    int lowestSlot = -1;
                    int lowestCount = Integer.MAX_VALUE;
                    for (int slot : group) {
                        int count = craftingInput.getStack(slot).getCount();
                        if (count < targetPerSlot && count < lowestCount) {
                            lowestSlot = slot;
                            lowestCount = count;
                        }
                    }
                    if (lowestSlot < 0) {
                        throw new IllegalStateException(
                                "TriStorage quick-craft refill exceeded its grid target");
                    }
                    craftingInput.getStack(lowestSlot).increment(1);
                    pulled--;
                }
            }
        } finally {
            recipeTransferInProgress = false;
        }
        updateCraftingResult();
    }

    private void collectMatchingRealStacks(PlayerEntity player) {
        ItemStack cursor = getCursorStack();
        if (cursor.isEmpty()) {
            return;
        }
        int[] starts = {CRAFT_INPUT_START, PLAYER_START};
        int[] ends = {CRAFT_INPUT_END, PLAYER_END};
        for (int pass = 0; pass < 2 && cursor.getCount() < cursor.getMaxCount(); pass++) {
            for (int range = 0; range < starts.length; range++) {
                for (int index = starts[range]; index < ends[range]
                        && cursor.getCount() < cursor.getMaxCount(); index++) {
                    Slot slot = slots.get(index);
                    ItemStack candidate = slot.getStack();
                    if (candidate.isEmpty() || !ItemStack.canCombine(cursor, candidate)
                            || !slot.canTakeItems(player)
                            || (pass == 0 && candidate.getCount() == candidate.getMaxCount())) {
                        continue;
                    }
                    int amount = Math.min(candidate.getCount(),
                            cursor.getMaxCount() - cursor.getCount());
                    ItemStack taken = slot.takeStack(amount);
                    cursor.increment(taken.getCount());
                    slot.onTakeItem(player, taken);
                }
            }
        }
        setCursorStack(cursor);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        if (slotIndex < CRAFT_INPUT_START) {
            return super.quickMove(player, slotIndex);
        }

        Slot slot = slots.get(slotIndex);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();

        if (slotIndex == RESULT_SLOT) {
            source.getItem().onCraft(source, player.getWorld(), player);
            if (!insertItem(source, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickTransfer(source, original);
        } else {
            boolean moved = false;
            if (coreIsAttached()) {
                long inserted = core.insert(source, source.getCount());
                if (inserted > 0) {
                    source.decrement((int) inserted);
                    moved = true;
                    requestStorageRefresh();
                }
            }
            if (!source.isEmpty()
                    && insertItem(source, PLAYER_START, PLAYER_END, false)) {
                moved = true;
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (source.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        if (source.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTakeItem(player, source);
        if (slotIndex == RESULT_SLOT && !source.isEmpty()) {
            player.dropItem(source, false);
        }
        return original;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        returnCraftingInput(player);
        craftingResult.clear();
        super.onClosed(player);
    }

    private void returnCraftingInput(PlayerEntity player) {
        Runnable returnItems = () -> {
            List<ItemStack> remainingStacks = new ArrayList<>();
            for (int slot = 0; slot < craftingInput.size(); slot++) {
                ItemStack remaining = craftingInput.removeStack(slot);
                if (!remaining.isEmpty()) {
                    remainingStacks.add(remaining);
                }
            }
            returnStacks(player, remainingStacks);
        };
        if (coreIsAttached()) {
            core.batchMutations(returnItems);
        } else {
            returnItems.run();
        }
    }

    private boolean coreIsAttached() {
        return core != null && !core.isRemoved() && core.getWorld() != null
                && core.getWorld().getBlockEntity(core.getPos()) == core;
    }

    private final class AutoRefillingResultSlot extends CraftingResultSlot {
        private AutoRefillingResultSlot(PlayerEntity player,
                                        CraftingInventory input,
                                        CraftingResultInventory result,
                                        int index, int x, int y) {
            super(player, input, result, index, x, y);
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack craftedStack) {
            List<ItemStack> templates = copyStacks(craftingInput.size(),
                    craftingInput::getStack);
            recipeTransferInProgress = true;
            boolean refilled = false;
            try {
                // Vanilla remains responsible for consuming ingredients and
                // placing recipe remainders such as buckets or reusable tools.
                super.onTakeItem(player, craftedStack);
                if (coreIsAttached() && !player.getWorld().isClient) {
                    for (int slot = 0; slot < craftingInput.size(); slot++) {
                        ItemStack before = templates.get(slot);
                        ItemStack after = craftingInput.getStack(slot);
                        if (!CraftingRefillPlanner.shouldRefill(before, after)) {
                            continue;
                        }
                        ItemStack extracted = core.extractMatching(before, 1);
                        if (!extracted.isEmpty()) {
                            craftingInput.setStack(slot, extracted);
                            refilled = true;
                        }
                    }
                }
            } finally {
                recipeTransferInProgress = false;
            }
            updateCraftingResult();
            if (refilled) {
                StorageMetrics.increment("crafting.auto_refills");
                requestStorageRefresh();
            }
        }
    }

    private record TransferResources(
            List<RecipeTransferPlanner.Resource> resources,
            Map<ItemKey, StorageRuntime.SnapshotEntry> storageEntries) {
    }

    private record RecipeAvailabilityKey(Identifier recipeId, long storageEpoch,
                                         long localFingerprint) {
    }
}
