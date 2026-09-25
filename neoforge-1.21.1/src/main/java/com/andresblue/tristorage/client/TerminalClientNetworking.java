package com.andresblue.tristorage.client;

import com.andresblue.tristorage.network.TerminalPackets;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class TerminalClientNetworking {
    private TerminalClientNetworking() {
    }

    /** Runs on the client main thread via the payload handler's enqueueWork. */
    public static void acceptFilterState(TerminalPackets.FilterStatePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof AbstractTerminalScreen<?> terminal
                && terminal.getMenu().containerId == payload.syncId()) {
            terminal.acceptFilterState(
                    payload.sequence(),
                    TerminalFilter.CategoryMode.byNetworkId(payload.mode()),
                    payload.selectedCategory(),
                    List.copyOf(payload.categories()));
        }
    }

    static void sendFilter(int syncId, int sequence, String query,
                           TerminalFilter.CategoryMode mode, String category) {
        PacketDistributor.sendToServer(new TerminalPackets.FilterUpdatePayload(
                syncId, sequence, query, mode.ordinal(), category));
    }
}
