package com.andresblue.tristorage.platform.forge;

//? if forge {
/*import com.andresblue.tristorage.platform.ClientPlatformHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ForgeClientPlatformHooks implements ClientPlatformHooks {
    private final Map<ResourceLocation, ClientReceiver> receivers = new ConcurrentHashMap<>();

    public ForgeClientPlatformHooks() {
        ForgePlatformHooks.clientDispatcher = (id, payload) -> {
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
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(ForgePlatformHooks.CHANNEL,
                    ForgePlatformHooks.frame(id, buffer)));
        }
    }

    @Override
    public void onClientTickEnd(Consumer<Minecraft> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                TickEvent.ClientTickEvent.class, event -> {
                    if (event.phase == TickEvent.Phase.END) {
                        listener.accept(Minecraft.getInstance());
                    }
                });
    }
}
*///?}
