package com.andresblue.tristorage.network;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.screen.CraftingTerminalScreenHandler;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public final class TerminalPackets {
    private TerminalPackets() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(FilterRequest.ID, FilterRequest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FilterRequest.ID, (payload, context) ->
                context.server().execute(() -> {
                    if (context.player().currentScreenHandler.syncId != payload.syncId()) {
                        return;
                    }
                    TerminalFilter.CategoryMode mode =
                            TerminalFilter.CategoryMode.byNetworkId(payload.mode());
                    if (context.player().currentScreenHandler instanceof TerminalScreenHandler terminal) {
                        terminal.applyClientFilter(payload.query(), mode);
                    } else if (context.player().currentScreenHandler
                            instanceof CraftingTerminalScreenHandler crafting) {
                        crafting.applyClientFilter(payload.query(), mode);
                    }
                }));
    }

    public record FilterRequest(int syncId, String query, int mode) implements CustomPayload {
        public static final Id<FilterRequest> ID = new Id<>(TriStorageMod.id("terminal_filter"));
        public static final PacketCodec<RegistryByteBuf, FilterRequest> CODEC = PacketCodec.tuple(
                PacketCodecs.SYNC_ID, FilterRequest::syncId,
                PacketCodecs.string(TerminalFilter.MAX_QUERY_LENGTH), FilterRequest::query,
                PacketCodecs.VAR_INT, FilterRequest::mode,
                FilterRequest::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
