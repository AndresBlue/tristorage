package com.andresblue.tristorage.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/** Loader services used only by client code. */
public interface ClientPlatformHooks {
    /**
     * Registers a server-to-client packet. The receiver runs on the network
     * thread; handlers hop to the client thread themselves.
     */
    void registerClientReceiver(ResourceLocation id, ClientReceiver receiver);

    void sendToServer(ResourceLocation id, FriendlyByteBuf buffer);

    void onClientTickEnd(Consumer<Minecraft> listener);

    @FunctionalInterface
    interface ClientReceiver {
        void receive(Minecraft client, FriendlyByteBuf buffer);
    }
}
