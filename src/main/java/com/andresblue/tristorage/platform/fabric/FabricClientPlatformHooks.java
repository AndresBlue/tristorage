package com.andresblue.tristorage.platform.fabric;

//? if fabric {
import com.andresblue.tristorage.platform.ClientPlatformHooks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public final class FabricClientPlatformHooks implements ClientPlatformHooks {
    @Override
    public void registerClientReceiver(ResourceLocation id, ClientReceiver receiver) {
        ClientPlayNetworking.registerGlobalReceiver(id,
                (client, handler, buffer, sender) -> receiver.receive(client, buffer));
    }

    @Override
    public void sendToServer(ResourceLocation id, FriendlyByteBuf buffer) {
        ClientPlayNetworking.send(id, buffer);
    }

    @Override
    public void onClientTickEnd(Consumer<Minecraft> listener) {
        ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
    }
}
//?}
