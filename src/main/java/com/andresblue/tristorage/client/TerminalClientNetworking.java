package com.andresblue.tristorage.client;

import com.andresblue.tristorage.network.TerminalPackets;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TerminalClientNetworking {
    private static final long RECIPE_FILL_TIMEOUT_MS = 5_000L;
    private static final long RECIPE_VIEWER_REFRESH_DEBOUNCE_MS = 150L;
    private static int nextRecipeFillRequestId;
    private static PendingRecipeFill pendingRecipeFill;
    private static boolean recipeViewerRefreshPending;
    private static long recipeViewerRefreshAt;
    private static Method emiRefreshMethod;
    private static boolean emiRefreshLookupFailed;
    private static WeakReference<AbstractTerminalScreen<?>> terminalScreenReference =
            new WeakReference<>(null);
    private static final long AVAILABILITY_RETRY_MS = 1_000L;
    private static final int MAX_AVAILABILITY_CACHE = 128;
    private static final Map<AvailabilityKey, RecipeAvailability> AVAILABILITY_CACHE =
            new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<AvailabilityKey, Long> AVAILABILITY_PENDING =
            new LinkedHashMap<>();

    private TerminalClientNetworking() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = Util.getMillis();
            if (pendingRecipeFill != null
                    && now - pendingRecipeFill.sentAt()
                    >= RECIPE_FILL_TIMEOUT_MS) {
                pendingRecipeFill = null;
            }
            if (recipeViewerRefreshPending && now >= recipeViewerRefreshAt) {
                recipeViewerRefreshPending = false;
                refreshRecipeViewerButtons(client, null);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(TerminalPackets.FILTER_STATE,
                (client, networkHandler, buffer, responseSender) -> {
                    int syncId = buffer.readVarInt();
                    int sequence = buffer.readVarInt();
                    TerminalFilter.CategoryMode mode = TerminalFilter.CategoryMode.byNetworkId(
                            buffer.readVarInt());
                    String selected = buffer.readUtf(TerminalFilter.MAX_CATEGORY_LENGTH);
                    int count = Math.min(buffer.readVarInt(), 512);
                    List<String> categories = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        categories.add(buffer.readUtf(TerminalFilter.MAX_CATEGORY_LENGTH));
                    }
                    client.execute(() -> {
                        AbstractTerminalScreen<?> terminal = terminalScreen(
                                client, syncId);
                        if (terminal != null) {
                            terminal.acceptFilterState(
                                    sequence, mode, selected, List.copyOf(categories));
                        }
                    });
                });
        ClientPlayNetworking.registerGlobalReceiver(TerminalPackets.PAGE_STATE,
                (client, networkHandler, buffer, responseSender) -> {
                    int syncId = buffer.readVarInt();
                    UUID storageId = buffer.readUUID();
                    long revision = buffer.readLong();
                    long[] entryIds = new long[TerminalScreenHandler.PAGE_SIZE];
                    for (int slot = 0; slot < entryIds.length; slot++) {
                        entryIds[slot] = buffer.readVarLong();
                    }
                    client.execute(() -> {
                        if (client.player != null
                                && client.player.containerMenu
                                instanceof TerminalScreenHandler terminal
                                && terminal.containerId == syncId) {
                            long previousRevision = terminal.clientPageRevision();
                            terminal.acceptClientPageState(storageId, revision, entryIds);
                            if (previousRevision != revision) {
                                invalidateAvailability(syncId, revision);
                                if (previousRevision != Long.MIN_VALUE) {
                                    scheduleRecipeViewerRefresh(client);
                                }
                            }
                        }
                    });
                });
        ClientPlayNetworking.registerGlobalReceiver(TerminalPackets.RECIPE_AVAILABILITY_STATE,
                (client, networkHandler, buffer, responseSender) -> {
                    int syncId = buffer.readVarInt();
                    long revision = buffer.readLong();
                    long localFingerprint = buffer.readLong();
                    ResourceLocation recipeId = buffer.readResourceLocation();
                    int requiredMask = buffer.readVarInt() & 0x1FF;
                    int availableMask = buffer.readVarInt() & requiredMask;
                    client.execute(() -> {
                        if (client.player == null
                                || !(client.player.containerMenu
                                instanceof CraftingTerminalScreenHandler terminal)
                                || terminal.containerId != syncId
                                || terminal.clientPageRevision() != revision
                                || terminal.recipeTransferClientFingerprint()
                                != localFingerprint) {
                            return;
                        }
                        AvailabilityKey key = new AvailabilityKey(
                                syncId, terminal.clientStorageUuid(), revision,
                                localFingerprint, recipeId);
                        AVAILABILITY_PENDING.remove(key);
                        AVAILABILITY_CACHE.put(key,
                                new RecipeAvailability(requiredMask, availableMask));
                        trimAvailabilityCache();
                        refreshRecipeViewerButtons(client, recipeId);
                    });
                });
        ClientPlayNetworking.registerGlobalReceiver(TerminalPackets.RECIPE_FILL_STATE,
                (client, networkHandler, buffer, responseSender) -> {
                    int syncId = buffer.readVarInt();
                    int requestId = buffer.readVarInt();
                    ResourceLocation recipeId = buffer.readResourceLocation();
                    buffer.readBoolean();
                    client.execute(() -> {
                        if (pendingRecipeFill != null
                                && pendingRecipeFill.syncId() == syncId
                                && pendingRecipeFill.requestId() == requestId
                                && pendingRecipeFill.recipeId().equals(recipeId)) {
                            pendingRecipeFill = null;
                        }
                    });
                });
    }

    static void registerTerminalScreen(AbstractTerminalScreen<?> terminal) {
        AbstractTerminalScreen<?> previous = terminalScreenReference.get();
        if (previous == null
                || previous.getMenu() != terminal.getMenu()) {
            // syncId values are reused after reopening containers. Never let a
            // result from an older terminal/world satisfy the new screen.
            AVAILABILITY_CACHE.clear();
            AVAILABILITY_PENDING.clear();
            pendingRecipeFill = null;
        }
        terminalScreenReference = new WeakReference<>(terminal);
    }

    private static AbstractTerminalScreen<?> terminalScreen(
            net.minecraft.client.Minecraft client, int syncId) {
        if (client.screen instanceof AbstractTerminalScreen<?> current
                && current.getMenu().containerId == syncId) {
            return current;
        }
        AbstractTerminalScreen<?> terminal = terminalScreenReference.get();
        if (terminal == null || client.player == null
                || terminal.getMenu().containerId != syncId
                || client.player.containerMenu
                != terminal.getMenu()) {
            return null;
        }
        return terminal;
    }

    static void sendFilter(int syncId, int sequence, String query,
                           TerminalFilter.CategoryMode mode, String category) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(syncId);
        buffer.writeVarInt(sequence);
        buffer.writeUtf(query, TerminalFilter.MAX_QUERY_LENGTH);
        buffer.writeVarInt(mode.ordinal());
        buffer.writeUtf(category, TerminalFilter.MAX_CATEGORY_LENGTH);
        ClientPlayNetworking.send(TerminalPackets.FILTER_UPDATE, buffer);
    }

    static void sendVirtualAction(TerminalScreenHandler handler, int slot,
                                  int button, ClickType actionType) {
        UUID storageId = handler.clientStorageUuid();
        if (storageId == null || !handler.hasAuthoritativePageState()) {
            return;
        }
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(handler.containerId);
        buffer.writeUUID(storageId);
        buffer.writeLong(handler.clientPageRevision());
        buffer.writeVarInt(slot);
        buffer.writeVarLong(handler.clientEntryId(slot));
        buffer.writeVarInt(button);
        buffer.writeVarInt(actionType.ordinal());
        ClientPlayNetworking.send(TerminalPackets.VIRTUAL_ACTION, buffer);
    }

    public static void sendRecipeFill(int syncId, ResourceLocation recipeId,
                                      int requestedCrafts) {
        if (recipeId == null) {
            return;
        }
        long now = Util.getMillis();
        if (pendingRecipeFill != null) {
            if (pendingRecipeFill.syncId() == syncId
                    && now - pendingRecipeFill.sentAt()
                    < RECIPE_FILL_TIMEOUT_MS) {
                return;
            }
            pendingRecipeFill = null;
        }
        int requestId = ++nextRecipeFillRequestId;
        pendingRecipeFill = new PendingRecipeFill(
                syncId, requestId, recipeId, now);
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(syncId);
        buffer.writeVarInt(requestId);
        buffer.writeResourceLocation(recipeId);
        buffer.writeInt(requestedCrafts);
        ClientPlayNetworking.send(TerminalPackets.RECIPE_FILL, buffer);
    }

    /**
     * Returns the cached server-authoritative preflight for a recipe, or null
     * while the compact nine-bit response is in flight.
     */
    public static RecipeAvailability recipeAvailability(
            CraftingTerminalScreenHandler handler, ResourceLocation recipeId) {
        if (handler == null || recipeId == null) {
            return null;
        }
        long revision = handler.clientPageRevision();
        if (revision == Long.MIN_VALUE) {
            return null;
        }
        long localFingerprint = handler.recipeTransferClientFingerprint();
        AvailabilityKey key = new AvailabilityKey(
                handler.containerId, handler.clientStorageUuid(), revision,
                localFingerprint, recipeId);
        RecipeAvailability cached = AVAILABILITY_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        long now = Util.getMillis();
        long lastRequest = AVAILABILITY_PENDING.getOrDefault(key, Long.MIN_VALUE);
        if (lastRequest == Long.MIN_VALUE || now - lastRequest >= AVAILABILITY_RETRY_MS) {
            AVAILABILITY_PENDING.put(key, now);
            FriendlyByteBuf buffer = PacketByteBufs.create();
            buffer.writeVarInt(handler.containerId);
            buffer.writeLong(revision);
            buffer.writeLong(localFingerprint);
            buffer.writeResourceLocation(recipeId);
            ClientPlayNetworking.send(TerminalPackets.RECIPE_AVAILABILITY, buffer);
        }
        return null;
    }

    private static void invalidateAvailability(int syncId, long currentRevision) {
        AVAILABILITY_CACHE.keySet().removeIf(key -> key.syncId == syncId
                && key.revision != currentRevision);
        AVAILABILITY_PENDING.keySet().removeIf(key -> key.syncId == syncId
                && key.revision != currentRevision);
    }

    private static void trimAvailabilityCache() {
        Iterator<AvailabilityKey> iterator = AVAILABILITY_CACHE.keySet().iterator();
        while (AVAILABILITY_CACHE.size() > MAX_AVAILABILITY_CACHE
                && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static void scheduleRecipeViewerRefresh(
            net.minecraft.client.Minecraft client) {
        if (client.screen == null
                || !client.screen.getClass().getName()
                .equals("dev.emi.emi.screen.RecipeScreen")) {
            return;
        }
        recipeViewerRefreshPending = true;
        recipeViewerRefreshAt = Util.getMillis()
                + RECIPE_VIEWER_REFRESH_DEBOUNCE_MS;
    }

    /**
     * Refreshes only EMI's fill widget. A full Screen#resize reconstructs the
     * complete recipe page and the underlying terminal; repeating that for
     * every storage revision caused unbounded render work in large modpacks.
     */
    private static void refreshRecipeViewerButtons(
            net.minecraft.client.Minecraft client,
            ResourceLocation recipeId) {
        if (client.screen == null
                || !client.screen.getClass().getName()
                .equals("dev.emi.emi.screen.RecipeScreen")
                || emiRefreshLookupFailed) {
            return;
        }
        try {
            if (emiRefreshMethod == null) {
                Class<?> plugin = Class.forName(
                        "com.andresblue.tristorage.compat.emi.TriStorageEmiPlugin");
                emiRefreshMethod = plugin.getMethod(
                        "refreshRecipeFillButtons", ResourceLocation.class);
            }
            emiRefreshMethod.invoke(null, recipeId);
        } catch (ReflectiveOperationException | LinkageError exception) {
            // EMI is optional. If its internals differ, leave its current
            // button untouched rather than rebuilding an entire recipe page.
            emiRefreshLookupFailed = true;
        }
    }

    public record RecipeAvailability(int requiredMask, int availableMask) {
        public boolean canCraft() {
            return requiredMask != 0
                    && (availableMask & requiredMask) == requiredMask;
        }

        public boolean isAvailable(int inputIndex) {
            int bit = 1 << inputIndex;
            return (requiredMask & bit) == 0 || (availableMask & bit) != 0;
        }
    }

    private record AvailabilityKey(int syncId, UUID storageId, long revision,
                                   long localFingerprint, ResourceLocation recipeId) {
    }

    private record PendingRecipeFill(int syncId, int requestId,
                                     ResourceLocation recipeId, long sentAt) {
    }
}
