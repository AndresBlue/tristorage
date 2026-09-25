package com.andresblue.tristorage.platform.fabric;

//? if fabric {
import com.andresblue.tristorage.platform.ClientPlatformHooks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class FabricClientPlatformHooks implements ClientPlatformHooks {
    //? if >=1.20.5 {
    /*private final Map<ResourceLocation, ClientReceiver> receivers = new ConcurrentHashMap<>();

    public FabricClientPlatformHooks() {
        ClientPlayNetworking.registerGlobalReceiver(FabricPayload.TO_CLIENT, (payload, context) -> {
            ClientReceiver receiver = receivers.get(payload.channel());
            if (receiver != null) {
                receiver.receive(context.client(), payload.buffer());
            }
        });
    }

    @Override
    public void registerClientReceiver(ResourceLocation id, ClientReceiver receiver) {
        receivers.put(id, receiver);
    }

    @Override
    public void sendToServer(ResourceLocation id, FriendlyByteBuf buffer) {
        ClientPlayNetworking.send(FabricPayload.wrap(FabricPayload.TO_SERVER, id, buffer));
    }
    *///?} else {
    @Override
    public void registerClientReceiver(ResourceLocation id, ClientReceiver receiver) {
        ClientPlayNetworking.registerGlobalReceiver(id,
                (client, handler, buffer, sender) -> receiver.receive(client, buffer));
    }

    @Override
    public void sendToServer(ResourceLocation id, FriendlyByteBuf buffer) {
        ClientPlayNetworking.send(id, buffer);
    }
    //?}

    @Override
    public void onClientTickEnd(Consumer<Minecraft> listener) {
        ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
    }
}
//?}
