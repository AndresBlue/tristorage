package com.andresblue.tristorage.network;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.screen.RecipeTransferPlanner;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.UUID;

public final class TerminalPackets {
    public static final Identifier FILTER_UPDATE = TriStorageMod.id("terminal_filter_update");
    public static final Identifier FILTER_STATE = TriStorageMod.id("terminal_filter_state");
    public static final Identifier PAGE_STATE = TriStorageMod.id("terminal_page_state");
    public static final Identifier VIRTUAL_ACTION = TriStorageMod.id("terminal_virtual_action");
    public static final Identifier RECIPE_FILL = TriStorageMod.id("terminal_recipe_fill");
    public static final Identifier RECIPE_FILL_STATE = TriStorageMod.id(
            "terminal_recipe_fill_state");
    public static final Identifier RECIPE_AVAILABILITY = TriStorageMod.id(
            "terminal_recipe_availability");
    public static final Identifier RECIPE_AVAILABILITY_STATE = TriStorageMod.id(
            "terminal_recipe_availability_state");
    private static final int MAX_SYNCED_CATEGORIES = 512;

    private TerminalPackets() {
    }

    public static void initializeServer() {
        ServerPlayNetworking.registerGlobalReceiver(FILTER_UPDATE,
                (server, player, networkHandler, buffer, responseSender) -> {
                    int syncId = buffer.readVarInt();
                    int sequence = buffer.readVarInt();
                    String query = buffer.readString(TerminalFilter.MAX_QUERY_LENGTH);
                    TerminalFilter.CategoryMode mode = TerminalFilter.CategoryMode.byNetworkId(
                            buffer.readVarInt());
                    String category = buffer.readString(TerminalFilter.MAX_CATEGORY_LENGTH);
                    server.execute(() -> {
                        if (player.currentScreenHandler instanceof TerminalScreenHandler terminal
                                && terminal.syncId == syncId) {
                            terminal.applyClientFilter(sequence, query, mode, category);
                        }
                    });
                });
        ServerPlayNetworking.registerGlobalReceiver(VIRTUAL_ACTION,
                (server, player, networkHandler, buffer, responseSender) -> {
                    int syncId = buffer.readVarInt();
                    UUID storageId = buffer.readUuid();
                    long clientRevision = buffer.readLong();
                    int slot = buffer.readVarInt();
                    long entryId = buffer.readVarLong();
                    int button = buffer.readVarInt();
                    int actionOrdinal = buffer.readVarInt();
                    server.execute(() -> {
                        if (!(player.currentScreenHandler instanceof TerminalScreenHandler terminal)
                                || terminal.syncId != syncId
                                || actionOrdinal < 0
                                || actionOrdinal >= SlotActionType.values().length) {
                            return;
                        }
                        terminal.applyVirtualAction(player, storageId, clientRevision,
                                slot, entryId, button, SlotActionType.values()[actionOrdinal]);
                    });
                });
        ServerPlayNetworking.registerGlobalReceiver(RECIPE_FILL,
                (server, player, networkHandler, buffer, responseSender) -> {
                    int syncId = buffer.readVarInt();
                    int requestId = buffer.readVarInt();
                    Identifier recipeId = buffer.readIdentifier();
                    int requested = buffer.readInt();
                    server.execute(() -> {
                        boolean success = false;
                        if (!(player.currentScreenHandler
                                instanceof CraftingTerminalScreenHandler terminal)
                                || terminal.syncId != syncId || !terminal.canUse(player)) {
                            sendRecipeFillState(player, syncId, requestId,
                                    recipeId, false);
                            return;
                        }
                        int safeRequested = requested == RecipeTransferPlanner.MAX_TRANSFER
                                ? RecipeTransferPlanner.MAX_TRANSFER
                                : Math.max(1, Math.min(64, requested));
                        Recipe<?> recipe = player.getServer().getRecipeManager()
                                .get(recipeId).orElse(null);
                        if (recipe instanceof CraftingRecipe craftingRecipe) {
                            success = terminal.fillRecipe(
                                    player, craftingRecipe, safeRequested);
                        }
                        sendRecipeFillState(player, syncId, requestId,
                                recipeId, success);
                    });
                });
        ServerPlayNetworking.registerGlobalReceiver(RECIPE_AVAILABILITY,
                (server, player, networkHandler, buffer, responseSender) -> {
                    int syncId = buffer.readVarInt();
                    long clientRevision = buffer.readLong();
                    long localFingerprint = buffer.readLong();
                    Identifier recipeId = buffer.readIdentifier();
                    server.execute(() -> {
                        if (!(player.currentScreenHandler
                                instanceof CraftingTerminalScreenHandler terminal)
                                || terminal.syncId != syncId || !terminal.canUse(player)) {
                            return;
                        }
                        if (!terminal.acceptRecipeAvailabilityRequest(
                                clientRevision, localFingerprint,
                                player.getServerWorld().getTime())) {
                            return;
                        }
                        Recipe<?> recipe = player.getServer().getRecipeManager()
                                .get(recipeId).orElse(null);
                        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
                            sendRecipeAvailabilityState(player, syncId, clientRevision,
                                    localFingerprint, recipeId,
                                    new RecipeTransferPlanner.Availability(0, 0));
                            return;
                        }
                        sendRecipeAvailabilityState(player, syncId, clientRevision,
                                localFingerprint, recipeId,
                                terminal.recipeAvailability(craftingRecipe));
                    });
                });
    }

    public static void sendFilterState(ServerPlayerEntity player,
                                       TerminalScreenHandler terminal) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(terminal.syncId);
        buffer.writeVarInt(terminal.filterSequence());
        buffer.writeVarInt(terminal.filterMode().ordinal());
        buffer.writeString(terminal.selectedFilterCategory(),
                TerminalFilter.MAX_CATEGORY_LENGTH);
        List<String> categories = terminal.filterCategories();
        int size = Math.min(categories.size(), MAX_SYNCED_CATEGORIES);
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            buffer.writeString(categories.get(index), TerminalFilter.MAX_CATEGORY_LENGTH);
        }
        ServerPlayNetworking.send(player, FILTER_STATE, buffer);
    }

    public static void sendPageState(ServerPlayerEntity player,
                                     TerminalScreenHandler terminal) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(terminal.syncId);
        buffer.writeUuid(terminal.storageUuid());
        buffer.writeLong(terminal.serverPageRevision());
        for (int slot = 0; slot < TerminalScreenHandler.PAGE_SIZE; slot++) {
            buffer.writeVarLong(terminal.serverEntryId(slot));
        }
        ServerPlayNetworking.send(player, PAGE_STATE, buffer);
    }

    private static void sendRecipeAvailabilityState(ServerPlayerEntity player,
                                                     int syncId, long clientRevision,
                                                     long localFingerprint,
                                                     Identifier recipeId,
                                                     RecipeTransferPlanner.Availability state) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(syncId);
        buffer.writeLong(clientRevision);
        buffer.writeLong(localFingerprint);
        buffer.writeIdentifier(recipeId);
        buffer.writeVarInt(state.requiredMask() & 0x1FF);
        buffer.writeVarInt(state.availableMask() & 0x1FF);
        ServerPlayNetworking.send(player, RECIPE_AVAILABILITY_STATE, buffer);
    }

    private static void sendRecipeFillState(ServerPlayerEntity player,
                                            int syncId, int requestId,
                                            Identifier recipeId,
                                            boolean success) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(syncId);
        buffer.writeVarInt(requestId);
        buffer.writeIdentifier(recipeId);
        buffer.writeBoolean(success);
        ServerPlayNetworking.send(player, RECIPE_FILL_STATE, buffer);
    }
}
