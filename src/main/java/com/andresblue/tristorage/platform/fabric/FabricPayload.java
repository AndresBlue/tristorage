package com.andresblue.tristorage.platform.fabric;

//? if fabric && >=1.20.5 {
/*import com.andresblue.tristorage.TriStorageMod;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/^*
 * Minecraft 1.20.5 made custom packets typed payloads. TriStorage packets are
 * still written as plain bytes by common code, so each direction has a single
 * payload type that carries the packet's channel id and those bytes.
 ^/
record FabricPayload(Type<FabricPayload> type, ResourceLocation channel, byte[] data)
        implements CustomPacketPayload {
    static final Type<FabricPayload> TO_SERVER = new Type<>(TriStorageMod.id("to_server"));
    static final Type<FabricPayload> TO_CLIENT = new Type<>(TriStorageMod.id("to_client"));

    static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(TO_SERVER, codec(TO_SERVER));
        PayloadTypeRegistry.playS2C().register(TO_CLIENT, codec(TO_CLIENT));
    }

    static FabricPayload wrap(Type<FabricPayload> type, ResourceLocation channel,
                              FriendlyByteBuf buffer) {
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        return new FabricPayload(type, channel, data);
    }

    FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
    }

    private static StreamCodec<FriendlyByteBuf, FabricPayload> codec(Type<FabricPayload> type) {
        return StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.channel);
                    buffer.writeByteArray(payload.data);
                },
                buffer -> new FabricPayload(type, buffer.readResourceLocation(),
                        buffer.readByteArray()));
    }
}
*///?}
