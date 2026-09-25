package com.andresblue.tristorage.screen;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.blockentity.StorageCoreBlockEntity;
import com.andresblue.tristorage.storage.StorageCategory;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.RecipeBookType;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class CraftingTerminalScreenHandler extends AbstractCraftingScreenHandler {
    public static final int RESULT_SLOT = 0;
    public static final int INPUT_START = 1;
    public static final int INPUT_END = 10;
    public static final int STORAGE_START = 10;
    public static final int STORAGE_END = STORAGE_START + TerminalScreenHandler.PAGE_SIZE;
    public static final int CATEGORY_START = STORAGE_END;
    public static final int PLAYER_START = CATEGORY_START + TerminalScreenHandler.CATEGORY_WINDOW;
    private static final int PROPERTY_COUNT = TerminalScreenHandler.PAGE_SIZE + 9;

    private final PlayerEntity player;
    private final StorageCoreBlockEntity core;
    private final SimpleInventory display = new SimpleInventory(
            TerminalScreenHandler.PAGE_SIZE + TerminalScreenHandler.CATEGORY_WINDOW);
    private final PropertyDelegate syncedProperties;
    private final List<String> keys = new ArrayList<>(Collections.nCopies(
            TerminalScreenHandler.PAGE_SIZE, ""));
    private final long[] counts = new long[TerminalScreenHandler.PAGE_SIZE];
    private List<StorageCategory> categories = List.of();
    private int page;
    private int categoryIndex;
    private int categoryWindowStart;
    private int filteredTypes;
    private long filteredItems;
    private int seenRevision = Integer.MIN_VALUE;
    private String query = "";
    private int sortMode;
    private TerminalFilter.CategoryMode categoryMode = TerminalFilter.CategoryMode.TYPE;
    private boolean filling;

    public CraftingTerminalScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, null);
    }

    public CraftingTerminalScreenHandler(int syncId, PlayerInventory playerInventory,
                                         StorageCoreBlockEntity core) {
        super(TriStorageMod.CRAFTING_TERMINAL_SCREEN_HANDLER, syncId, 3, 3);
        this.player = playerInventory.player;
        this.core = core;
        if (playerInventory.player instanceof ServerPlayerEntity serverPlayer) {
            TerminalFilter.prepareCreativeGroups(serverPlayer);
        }
        this.syncedProperties = core == null
                ? new ArrayPropertyDelegate(PROPERTY_COUNT)
                : properties(core);

        addResultSlot(player, 272, 38);
        addInputSlots(184, 20);
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new DisplaySlot(display, column + row * 9,
                        8 + column * 18, 18 + row * 18));
            }
        }
        for (int index = 0; index < TerminalScreenHandler.CATEGORY_WINDOW; index++) {
            addSlot(new DisplaySlot(display, TerminalScreenHandler.PAGE_SIZE + index,
                    6 + index * 24, -21));
        }
        addPlayerSlots(playerInventory, 8, 140);
        addProperties(syncedProperties);
        refreshPage(true);
    }

    @Override
    public void sendContentUpdates() {
        if (core != null && seenRevision != core.revision()) {
            refreshPage(true);
        }
        super.sendContentUpdates();
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        if (!filling && player.getEntityWorld() instanceof ServerWorld serverWorld) {
            updateCraftingResult(serverWorld, null);
        }
    }

    @Override
    protected void onInputSlotFillStart() {
        filling = true;
    }

    @Override
    protected void onInputSlotFillFinish(ServerWorld world, RecipeEntry<CraftingRecipe> recipe) {
        filling = false;
        updateCraftingResult(world, recipe);
    }

    private void updateCraftingResult(ServerWorld world, RecipeEntry<CraftingRecipe> lastRecipe) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        var input = craftingInventory.createRecipeInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeEntry<CraftingRecipe>> match = world.getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, world, lastRecipe);
        if (match.isPresent()
                && craftingResultInventory.shouldCraftRecipe(serverPlayer, match.get())) {
            ItemStack crafted = match.get().value().craft(input, world.getRegistryManager());
            if (crafted.isItemEnabled(world.getEnabledFeatures())) {
                result = crafted;
            }
        }
        craftingResultInventory.setStack(0, result);
        setReceivedStack(RESULT_SLOT, result);
        serverPlayer.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(
                syncId, nextRevision(), RESULT_SLOT, result));
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (core == null) {
            return false;
        }
        switch (id) {
            case 0 -> setPage(page - 1);
            case 1 -> setPage(page + 1);
            case 2 -> depositPlayerInventory();
            case 3 -> {
                sortMode = (sortMode + 1) % 3;
                page = 0;
                refreshPage(false);
            }
            case 4 -> setCategory(categoryIndex - 1);
            case 5 -> setCategory(categoryIndex + 1);
            case 6 -> setQuery("");
            case 701, 702 -> {
                categoryMode = TerminalFilter.CategoryMode.byNetworkId(id - 700);
                categoryIndex = 0;
                page = 0;
                refreshPage(true);
            }
            case 703 -> {
                categoryIndex = 0;
                page = 0;
                refreshPage(false);
            }
            case 100 -> {
                if (!query.isEmpty()) {
                    setQuery(query.substring(0, query.offsetByCodePoints(query.length(), -1)));
                }
            }
            default -> {
                if (id >= 1000 && id <= 1000 + Character.MAX_CODE_POINT
                        && query.codePointCount(0, query.length()) < TerminalFilter.MAX_QUERY_LENGTH) {
                    int codePoint = id - 1000;
                    if (!Character.isISOControl(codePoint)) {
                        setQuery(query + Character.toString(codePoint));
                    }
                } else {
                    return false;
                }
            }
        }
        sendContentUpdates();
        return true;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (core != null && slotIndex >= CATEGORY_START && slotIndex < PLAYER_START) {
            int selected = categoryWindowStart + slotIndex - CATEGORY_START;
            if (selected < categories.size()) {
                setCategory(selected);
                sendContentUpdates();
            }
            return;
        }
        if (core != null && slotIndex == RESULT_SLOT && actionType == SlotActionType.QUICK_MOVE) {
            ItemStack expected = slots.get(RESULT_SLOT).getStack().copy();
            for (int crafts = 0; crafts < 4096 && !expected.isEmpty(); crafts++) {
                ItemStack current = slots.get(RESULT_SLOT).getStack();
                if (current.isEmpty() || !ItemStack.areItemsAndComponentsEqual(expected, current)
                        || quickMove(player, RESULT_SLOT).isEmpty()) {
                    break;
                }
            }
            refreshPage(false);
            return;
        }
        if (core != null && actionType == SlotActionType.QUICK_MOVE
                && slotIndex >= 0 && slotIndex < slots.size()) {
            quickMove(player, slotIndex);
            return;
        }
        if (core != null && actionType == SlotActionType.PICKUP_ALL
                && (slotIndex >= STORAGE_START || !getCursorStack().isEmpty())) {
            core.batchMutations(() -> {
                if (slotIndex >= STORAGE_START && slotIndex < STORAGE_END) {
                    withdrawAllMatching(slotIndex - STORAGE_START, player);
                } else if (slotIndex >= PLAYER_START && slotIndex < slots.size()) {
                    depositAllMatching(player, slots.get(slotIndex).getStack());
                } else {
                    super.onSlotClick(slotIndex, button, actionType, player);
                }
            });
            refreshPage(false);
            return;
        }
        if (core != null && slotIndex >= STORAGE_START && slotIndex < STORAGE_END
                && actionType == SlotActionType.PICKUP) {
            int displayIndex = slotIndex - STORAGE_START;
            ItemStack cursor = getCursorStack();
            if (cursor.isEmpty()) {
                ItemStack extracted = extractSlot(displayIndex, button == 1 ? 1 : 64);
                if (!extracted.isEmpty()) {
                    setCursorStack(extracted);
                }
            } else {
                long inserted = core.insert(cursor, button == 1 ? 1 : cursor.getCount());
                cursor.decrement((int) inserted);
                setCursorStack(cursor);
            }
            refreshPage(false);
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (core == null || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        int revisionBefore = core.revision();
        ItemStack result = core.batchMutations(() -> quickMoveUnbatched(player, slotIndex));
        if (core.revision() != revisionBefore) refreshPage(false);
        return result;
    }

    private ItemStack quickMoveUnbatched(PlayerEntity player, int slotIndex) {
        Slot slot = slots.get(slotIndex);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        if (slotIndex == RESULT_SLOT) {
            ItemStack source = slot.getStack();
            ItemStack original = source.copy();
            source.getItem().onCraftByPlayer(source, player);
            if (!insertItem(source, PLAYER_START, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickTransfer(source, original);
            slot.onTakeItem(player, source);
            return original;
        }
        if (slotIndex >= STORAGE_START && slotIndex < STORAGE_END) {
            int displayIndex = slotIndex - STORAGE_START;
            ItemStack extracted = extractSlot(displayIndex, 64);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack original = extracted.copy();
            if (!insertItem(extracted, PLAYER_START, slots.size(), true)) {
                core.insert(original, original.getCount());
                return ItemStack.EMPTY;
            }
            if (!extracted.isEmpty()) {
                core.insert(extracted, extracted.getCount());
            }
            return original;
        }
        if (slotIndex >= CATEGORY_START && slotIndex < PLAYER_START) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        if (slotIndex >= INPUT_START && slotIndex < INPUT_END) {
            boolean moved = false;
            long inserted = core.insert(source, source.getCount());
            if (inserted > 0L) {
                source.decrement((int) inserted);
                moved = true;
            }
            if (!source.isEmpty() && insertItem(source, PLAYER_START, slots.size(), false)) {
                moved = true;
            }
            if (!moved) return ItemStack.EMPTY;
        } else {
            long inserted = core.insert(source, source.getCount());
            if (inserted <= 0L) return ItemStack.EMPTY;
            source.decrement((int) inserted);
        }
        if (source.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return original;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        Runnable returnItems = () -> {
            for (int index = 0; index < craftingInventory.size(); index++) {
                ItemStack stack = craftingInventory.removeStack(index);
                if (stack.isEmpty()) continue;
                long inserted = core == null ? 0L : core.insert(stack, stack.getCount());
                stack.decrement((int) inserted);
                if (!stack.isEmpty()) player.getInventory().offerOrDrop(stack);
            }
        };
        if (core == null) returnItems.run();
        else core.batchMutations(returnItems);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return core == null || (!core.isRemoved() && player.squaredDistanceTo(
                core.getPos().getX() + 0.5,
                core.getPos().getY() + 0.5,
                core.getPos().getZ() + 0.5) <= 64.0);
    }

    @Override
    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return slot.inventory != craftingResultInventory && super.canInsertIntoSlot(stack, slot);
    }

    @Override
    public Slot getOutputSlot() { return slots.get(RESULT_SLOT); }

    @Override
    public List<Slot> getInputSlots() { return slots.subList(INPUT_START, INPUT_END); }

    @Override
    public RecipeBookType getCategory() { return RecipeBookType.CRAFTING; }

    @Override
    protected PlayerEntity getPlayer() { return player; }

    public int page() { return syncedProperties.get(TerminalScreenHandler.PAGE_SIZE); }
    public int pageCount() { return Math.max(1, syncedProperties.get(TerminalScreenHandler.PAGE_SIZE + 1)); }
    public int storedTypes() { return syncedProperties.get(TerminalScreenHandler.PAGE_SIZE + 2); }
    public int totalItems() { return syncedProperties.get(TerminalScreenHandler.PAGE_SIZE + 3); }
    public int categoryIndex() { return syncedProperties.get(TerminalScreenHandler.PAGE_SIZE + 4); }
    public int categoryCount() { return Math.max(1, syncedProperties.get(TerminalScreenHandler.PAGE_SIZE + 5)); }
    public int categoryWindowStart() { return syncedProperties.get(TerminalScreenHandler.PAGE_SIZE + 6); }
    public TerminalFilter.CategoryMode categoryMode() {
        return TerminalFilter.CategoryMode.byNetworkId(
                syncedProperties.get(TerminalScreenHandler.PAGE_SIZE + 7));
    }
    public int sortMode() { return syncedProperties.get(TerminalScreenHandler.PAGE_SIZE + 8); }
    public long displayCount(int index) { return Integer.toUnsignedLong(syncedProperties.get(index)); }

    public void applyClientFilter(String nextQuery, TerminalFilter.CategoryMode requestedMode) {
        if (core == null) return;
        String selected = categories.isEmpty() ? TerminalFilter.ALL
                : categories.get(Math.min(categoryIndex, categories.size() - 1)).id();
        TerminalFilter.Selection safe = TerminalFilter.sanitize(
                nextQuery, requestedMode, selected);
        query = safe.query();
        categoryMode = safe.mode();
        page = 0;
        refreshPage(false);
        sendContentUpdates();
    }

    private ItemStack extractSlot(int index, int requested) {
        String key = keys.get(index);
        return key.isEmpty() ? ItemStack.EMPTY : core.extract(key, requested);
    }

    private void setPage(int requested) {
        page = Math.max(0, Math.min(filteredPageCount() - 1, requested));
        refreshPage(false);
    }

    private void setCategory(int requested) {
        categoryIndex = Math.max(0, Math.min(categories.size() - 1, requested));
        page = 0;
        refreshPage(false);
    }

    private void setQuery(String next) {
        query = TerminalFilter.normalize(next);
        page = 0;
        refreshPage(false);
    }

    private void depositPlayerInventory() {
        core.batchMutations(() -> {
            for (int index = PLAYER_START; index < slots.size(); index++) {
                Slot slot = slots.get(index);
                if (!slot.hasStack()) continue;
                ItemStack source = slot.getStack();
                long inserted = core.insert(source, source.getCount());
                source.decrement((int) inserted);
                slot.markDirty();
            }
        });
        refreshPage(false);
    }

    private void refreshPage(boolean rebuildCategories) {
        if (core == null) return;
        String selected = categories.isEmpty() ? TerminalFilter.ALL
                : categories.get(Math.min(categoryIndex, categories.size() - 1)).id();
        StorageCoreBlockEntity.EntryOrder order = switch (sortMode) {
            case 1 -> StorageCoreBlockEntity.EntryOrder.COUNT;
            case 2 -> StorageCoreBlockEntity.EntryOrder.RECENT;
            default -> StorageCoreBlockEntity.EntryOrder.REGISTRY;
        };
        StorageCoreBlockEntity.BrowseResult result = core.browse(page,
                TerminalScreenHandler.PAGE_SIZE, order,
                TerminalFilter.sanitize(query, categoryMode, selected));
        page = result.page();
        filteredTypes = result.filteredTypes();
        filteredItems = result.filteredItems();
        categories = result.categories();
        categoryIndex = 0;
        for (int index = 0; index < categories.size(); index++) {
            if (categories.get(index).id().equals(result.selectedCategory())) {
                categoryIndex = index;
                break;
            }
        }
        List<StorageCoreBlockEntity.EntryView> views = result.entries();
        for (int index = 0; index < TerminalScreenHandler.PAGE_SIZE; index++) {
            if (index < views.size()) {
                var view = views.get(index);
                display.setStack(index, view.stack().copyWithCount(1));
                keys.set(index, view.key());
                counts[index] = view.count();
            } else {
                display.setStack(index, ItemStack.EMPTY);
                keys.set(index, "");
                counts[index] = 0;
            }
        }
        categoryWindowStart = Math.max(0, Math.min(
                categoryIndex - TerminalScreenHandler.CATEGORY_WINDOW / 2,
                categories.size() - TerminalScreenHandler.CATEGORY_WINDOW));
        for (int index = 0; index < TerminalScreenHandler.CATEGORY_WINDOW; index++) {
            int sourceIndex = categoryWindowStart + index;
            display.setStack(TerminalScreenHandler.PAGE_SIZE + index,
                    sourceIndex < categories.size() ? categories.get(sourceIndex).icon().copy() : ItemStack.EMPTY);
        }
        seenRevision = core.revision();
    }

    private int filteredPageCount() {
        return Math.max(1, (filteredTypes + TerminalScreenHandler.PAGE_SIZE - 1)
                / TerminalScreenHandler.PAGE_SIZE);
    }

    private void withdrawAllMatching(int displayIndex, PlayerEntity player) {
        String key = keys.get(displayIndex);
        ItemStack shown = slots.get(STORAGE_START + displayIndex).getStack();
        if (key.isEmpty() || shown.isEmpty()) return;
        ItemStack cursor = getCursorStack();
        if (!cursor.isEmpty()) {
            if (!ItemStack.areItemsAndComponentsEqual(cursor, shown)) return;
            player.getInventory().insertStack(cursor);
            setCursorStack(cursor);
            if (!cursor.isEmpty()) return;
        }
        while (true) {
            ItemStack extracted = core.extract(key, shown.getMaxCount());
            if (extracted.isEmpty()) break;
            player.getInventory().insertStack(extracted);
            if (!extracted.isEmpty()) {
                core.insert(extracted, extracted.getCount());
                break;
            }
        }
        player.getInventory().markDirty();
    }

    private void depositAllMatching(PlayerEntity player, ItemStack clicked) {
        ItemStack cursor = getCursorStack();
        ItemStack reference = (cursor.isEmpty() ? clicked : cursor).copyWithCount(1);
        if (reference.isEmpty()) return;
        if (!cursor.isEmpty() && ItemStack.areItemsAndComponentsEqual(cursor, reference)) {
            long inserted = core.insert(cursor, cursor.getCount());
            cursor.decrement((int) inserted);
            setCursorStack(cursor);
        }
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, reference)) {
                long inserted = core.insert(stack, stack.getCount());
                stack.decrement((int) inserted);
            }
        }
        player.getInventory().markDirty();
    }

    private PropertyDelegate properties(StorageCoreBlockEntity core) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                if (index < TerminalScreenHandler.PAGE_SIZE) return (int) counts[index];
                return switch (index - TerminalScreenHandler.PAGE_SIZE) {
                    case 0 -> page;
                    case 1 -> filteredPageCount();
                    case 2 -> filteredTypes;
                    case 3 -> (int) Math.min(Integer.MAX_VALUE, filteredItems);
                    case 4 -> categoryIndex;
                    case 5 -> categories.size();
                    case 6 -> categoryWindowStart;
                    case 7 -> categoryMode.ordinal();
                    case 8 -> sortMode;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int size() { return PROPERTY_COUNT; }
        };
    }

    private static final class DisplaySlot extends Slot {
        private DisplaySlot(SimpleInventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        @Override public boolean canInsert(ItemStack stack) { return false; }
        @Override public boolean canTakeItems(PlayerEntity player) { return false; }
    }
}
