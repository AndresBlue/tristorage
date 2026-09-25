package com.andresblue.tristorage.platform;

//? if >=1.20.5 {
/*import com.andresblue.tristorage.TriStorageMod;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/^*
 * Minecraft 1.20.5 made custom packets typed payloads. TriStorage packets are
 * still written as plain bytes by common code, so each direction has a single
 * payload type that carries the packet's channel id and those bytes. Each
 * loader registers the two types its own way.
 ^/
public record ChannelPayload(Type<ChannelPayload> type, ResourceLocation channel, byte[] data)
        implements CustomPacketPayload {
    public static final Type<ChannelPayload> TO_SERVER = new Type<>(TriStorageMod.id("to_server"));
    public static final Type<ChannelPayload> TO_CLIENT = new Type<>(TriStorageMod.id("to_client"));

    public static ChannelPayload wrap(Type<ChannelPayload> type, ResourceLocation channel,
                                      FriendlyByteBuf buffer) {
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        return new ChannelPayload(type, channel, data);
    }

    public FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
    }

    public static StreamCodec<FriendlyByteBuf, ChannelPayload> codec(Type<ChannelPayload> type) {
        return StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeResourceLocation(payload.channel);
                    buffer.writeByteArray(payload.data);
                },
                buffer -> new ChannelPayload(type, buffer.readResourceLocation(),
                        buffer.readByteArray()));
    }
}
*///?}
