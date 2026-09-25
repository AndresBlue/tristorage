package com.andresblue.tristorage.network;

import com.andresblue.tristorage.version.Players;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.screen.RecipeTransferPlanner;
import com.andresblue.tristorage.storage.TerminalFilter;
import com.andresblue.tristorage.platform.Platform;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import com.andresblue.tristorage.version.Recipes;
import net.minecraft.world.item.crafting.CraftingRecipe;
import java.util.List;
import java.util.UUID;

public final class TerminalPackets {
    public static final ResourceLocation FILTER_UPDATE = TriStorageMod.id("terminal_filter_update");
    public static final ResourceLocation FILTER_STATE = TriStorageMod.id("terminal_filter_state");
    public static final ResourceLocation PAGE_STATE = TriStorageMod.id("terminal_page_state");
    public static final ResourceLocation VIRTUAL_ACTION = TriStorageMod.id("terminal_virtual_action");
    public static final ResourceLocation RECIPE_FILL = TriStorageMod.id("terminal_recipe_fill");
    public static final ResourceLocation RECIPE_FILL_STATE = TriStorageMod.id(
            "terminal_recipe_fill_state");
    public static final ResourceLocation RECIPE_AVAILABILITY = TriStorageMod.id(
            "terminal_recipe_availability");
    public static final ResourceLocation RECIPE_AVAILABILITY_STATE = TriStorageMod.id(
            "terminal_recipe_availability_state");
    private static final int MAX_SYNCED_CATEGORIES = 512;

    private TerminalPackets() {
    }

    public static void initializeServer() {
        Platform.get().registerServerReceiver(FILTER_UPDATE,
                (server, player, buffer) -> {
                    int syncId = buffer.readVarInt();
                    int sequence = buffer.readVarInt();
                    String query = buffer.readUtf(TerminalFilter.MAX_QUERY_LENGTH);
                    TerminalFilter.CategoryMode mode = TerminalFilter.CategoryMode.byNetworkId(
                            buffer.readVarInt());
                    String category = buffer.readUtf(TerminalFilter.MAX_CATEGORY_LENGTH);
                    server.execute(() -> {
                        if (player.containerMenu instanceof TerminalScreenHandler terminal
                                && terminal.containerId == syncId) {
                            terminal.applyClientFilter(sequence, query, mode, category);
                        }
                    });
                });
        Platform.get().registerServerReceiver(VIRTUAL_ACTION,
                (server, player, buffer) -> {
                    int syncId = buffer.readVarInt();
                    UUID storageId = buffer.readUUID();
                    long clientRevision = buffer.readLong();
                    int slot = buffer.readVarInt();
                    long entryId = buffer.readVarLong();
                    int button = buffer.readVarInt();
                    int actionOrdinal = buffer.readVarInt();
                    server.execute(() -> {
                        if (!(player.containerMenu instanceof TerminalScreenHandler terminal)
                                || terminal.containerId != syncId
                                || actionOrdinal < 0
                                || actionOrdinal >= ClickType.values().length) {
                            return;
                        }
                        terminal.applyVirtualAction(player, storageId, clientRevision,
                                slot, entryId, button, ClickType.values()[actionOrdinal]);
                    });
                });
        Platform.get().registerServerReceiver(RECIPE_FILL,
                (server, player, buffer) -> {
                    int syncId = buffer.readVarInt();
                    int requestId = buffer.readVarInt();
                    ResourceLocation recipeId = buffer.readResourceLocation();
                    int requested = buffer.readInt();
                    server.execute(() -> {
                        boolean success = false;
                        if (!(player.containerMenu
                                instanceof CraftingTerminalScreenHandler terminal)
                                || terminal.containerId != syncId || !terminal.stillValid(player)) {
                            sendRecipeFillState(player, syncId, requestId,
                                    recipeId, false);
                            return;
                        }
                        int safeRequested = requested == RecipeTransferPlanner.MAX_TRANSFER
                                ? RecipeTransferPlanner.MAX_TRANSFER
                                : Math.max(1, Math.min(64, requested));
                        CraftingRecipe craftingRecipe = Recipes.crafting(
                                Players.server(player).getRecipeManager(), recipeId);
                        if (craftingRecipe != null) {
                            success = terminal.fillRecipe(
                                    player, recipeId, craftingRecipe, safeRequested);
                        }
                        sendRecipeFillState(player, syncId, requestId,
                                recipeId, success);
                    });
                });
        Platform.get().registerServerReceiver(RECIPE_AVAILABILITY,
                (server, player, buffer) -> {
                    int syncId = buffer.readVarInt();
                    long clientRevision = buffer.readLong();
                    long localFingerprint = buffer.readLong();
                    ResourceLocation recipeId = buffer.readResourceLocation();
                    server.execute(() -> {
                        if (!(player.containerMenu
                                instanceof CraftingTerminalScreenHandler terminal)
                                || terminal.containerId != syncId || !terminal.stillValid(player)) {
                            return;
                        }
                        if (!terminal.acceptRecipeAvailabilityRequest(
                                clientRevision, localFingerprint,
                                Players.level(player).getGameTime())) {
                            return;
                        }
                        CraftingRecipe craftingRecipe = Recipes.crafting(
                                Players.server(player).getRecipeManager(), recipeId);
                        if (craftingRecipe == null) {
                            sendRecipeAvailabilityState(player, syncId, clientRevision,
                                    localFingerprint, recipeId,
                                    new RecipeTransferPlanner.Availability(0, 0));
                            return;
                        }
                        sendRecipeAvailabilityState(player, syncId, clientRevision,
                                localFingerprint, recipeId,
                                terminal.recipeAvailability(recipeId, craftingRecipe));
                    });
                });
    }

    public static void sendFilterState(ServerPlayer player,
                                       TerminalScreenHandler terminal) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(terminal.containerId);
        buffer.writeVarInt(terminal.filterSequence());
        buffer.writeVarInt(terminal.filterMode().ordinal());
        buffer.writeUtf(terminal.selectedFilterCategory(),
                TerminalFilter.MAX_CATEGORY_LENGTH);
        List<String> categories = terminal.filterCategories();
        int size = Math.min(categories.size(), MAX_SYNCED_CATEGORIES);
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            buffer.writeUtf(categories.get(index), TerminalFilter.MAX_CATEGORY_LENGTH);
        }
        Platform.get().sendToPlayer(player, FILTER_STATE, buffer);
    }

    public static void sendPageState(ServerPlayer player,
                                     TerminalScreenHandler terminal) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(terminal.containerId);
        buffer.writeUUID(terminal.storageUuid());
        buffer.writeLong(terminal.serverPageRevision());
        for (int slot = 0; slot < TerminalScreenHandler.PAGE_SIZE; slot++) {
            buffer.writeVarLong(terminal.serverEntryId(slot));
        }
        Platform.get().sendToPlayer(player, PAGE_STATE, buffer);
    }

    private static void sendRecipeAvailabilityState(ServerPlayer player,
                                                     int syncId, long clientRevision,
                                                     long localFingerprint,
                                                     ResourceLocation recipeId,
                                                     RecipeTransferPlanner.Availability state) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(syncId);
        buffer.writeLong(clientRevision);
        buffer.writeLong(localFingerprint);
        buffer.writeResourceLocation(recipeId);
        buffer.writeVarInt(state.requiredMask() & 0x1FF);
        buffer.writeVarInt(state.availableMask() & 0x1FF);
        Platform.get().sendToPlayer(player, RECIPE_AVAILABILITY_STATE, buffer);
    }

    private static void sendRecipeFillState(ServerPlayer player,
                                            int syncId, int requestId,
                                            ResourceLocation recipeId,
                                            boolean success) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(syncId);
        buffer.writeVarInt(requestId);
        buffer.writeResourceLocation(recipeId);
        buffer.writeBoolean(success);
        Platform.get().sendToPlayer(player, RECIPE_FILL_STATE, buffer);
    }
}
