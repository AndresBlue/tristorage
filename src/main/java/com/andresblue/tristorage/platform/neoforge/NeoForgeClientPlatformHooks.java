package com.andresblue.tristorage.platform.neoforge;

//? if neoforge {
/*import com.andresblue.tristorage.platform.ChannelPayload;
import com.andresblue.tristorage.platform.ClientPlatformHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
//? if >=26.1 {
/^import net.neoforged.neoforge.client.network.ClientPacketDistributor;
^///?} else {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class NeoForgeClientPlatformHooks implements ClientPlatformHooks {
    private final Map<ResourceLocation, ClientReceiver> receivers = new ConcurrentHashMap<>();

    public NeoForgeClientPlatformHooks() {
        NeoForgePlatformHooks.clientDispatcher = (id, payload) -> {
            ClientReceiver receiver = receivers.get(id);
            if (receiver != null) {
                receiver.receive(Minecraft.getInstance(), payload);
            }
        };
    }

    @Override
    public void registerClientReceiver(ResourceLocation id, ClientReceiver receiver) {
        receivers.put(id, receiver);
    }

    @Override
    public void sendToServer(ResourceLocation id, FriendlyByteBuf buffer) {
        //? if >=26.1 {
        /^ClientPacketDistributor.sendToServer(
                ChannelPayload.wrap(ChannelPayload.TO_SERVER, id, buffer));
        ^///?} else {
        PacketDistributor.sendToServer(ChannelPayload.wrap(ChannelPayload.TO_SERVER, id, buffer));
        //?}
    }

    @Override
    public void onClientTickEnd(Consumer<Minecraft> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                ClientTickEvent.Post.class, event -> listener.accept(Minecraft.getInstance()));
    }
}
*///?}
