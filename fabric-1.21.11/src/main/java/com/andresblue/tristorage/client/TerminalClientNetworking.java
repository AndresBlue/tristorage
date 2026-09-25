package com.andresblue.tristorage.client;

import com.andresblue.tristorage.network.TerminalPackets;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

final class TerminalClientNetworking {
    private TerminalClientNetworking() {
    }

    static void sendFilter(int syncId, String query, TerminalFilter.CategoryMode mode) {
        ClientPlayNetworking.send(new TerminalPackets.FilterRequest(
                syncId, query, mode.ordinal()));
    }
}
