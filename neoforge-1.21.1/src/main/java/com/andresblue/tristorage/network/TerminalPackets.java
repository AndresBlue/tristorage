package com.andresblue.tristorage.network;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.screen.TerminalScreenHandler;
import com.andresblue.tristorage.storage.TerminalFilter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

public final class TerminalPackets {
    private static final int MAX_SYNCED_CATEGORIES = 512;

    private TerminalPackets() {
    }

    public record FilterUpdatePayload(int syncId, int sequence, String query, int mode,
                                      String category) implements CustomPacketPayload {
        public static final Type<FilterUpdatePayload> TYPE =
                new Type<>(TriStorageMod.id("terminal_filter_update"));
        public static final StreamCodec<FriendlyByteBuf, FilterUpdatePayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, FilterUpdatePayload::syncId,
                        ByteBufCodecs.VAR_INT, FilterUpdatePayload::sequence,
                        ByteBufCodecs.stringUtf8(TerminalFilter.MAX_QUERY_LENGTH), FilterUpdatePayload::query,
                        ByteBufCodecs.VAR_INT, FilterUpdatePayload::mode,
                        ByteBufCodecs.stringUtf8(TerminalFilter.MAX_CATEGORY_LENGTH), FilterUpdatePayload::category,
                        FilterUpdatePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FilterStatePayload(int syncId, int sequence, int mode, String selectedCategory,
                                     List<String> categories) implements CustomPacketPayload {
        public static final Type<FilterStatePayload> TYPE =
                new Type<>(TriStorageMod.id("terminal_filter_state"));
        public static final StreamCodec<FriendlyByteBuf, FilterStatePayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, FilterStatePayload::syncId,
                        ByteBufCodecs.VAR_INT, FilterStatePayload::sequence,
                        ByteBufCodecs.VAR_INT, FilterStatePayload::mode,
                        ByteBufCodecs.stringUtf8(TerminalFilter.MAX_CATEGORY_LENGTH), FilterStatePayload::selectedCategory,
                        ByteBufCodecs.stringUtf8(TerminalFilter.MAX_CATEGORY_LENGTH)
                                .apply(ByteBufCodecs.list(MAX_SYNCED_CATEGORIES)), FilterStatePayload::categories,
                        FilterStatePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(FilterUpdatePayload.TYPE, FilterUpdatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.containerMenu instanceof TerminalScreenHandler terminal
                            && terminal.containerId == payload.syncId()) {
                        terminal.applyClientFilter(payload.sequence(), payload.query(),
                                TerminalFilter.CategoryMode.byNetworkId(payload.mode()),
                                payload.category());
                    }
                }));
        registrar.playToClient(FilterStatePayload.TYPE, FilterStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.andresblue.tristorage.client.TerminalClientNetworking
                                .acceptFilterState(payload)));
    }

    public static void sendFilterState(ServerPlayer player, TerminalScreenHandler terminal) {
        List<String> categories = terminal.filterCategories();
        int size = Math.min(categories.size(), MAX_SYNCED_CATEGORIES);
        PacketDistributor.sendToPlayer(player, new FilterStatePayload(
                terminal.containerId,
                terminal.filterSequence(),
                terminal.filterMode().ordinal(),
                terminal.selectedFilterCategory(),
                List.copyOf(categories.subList(0, size))));
    }
}
