package com.andresblue.tristorage.platform.fabric;

//? if fabric {
import com.andresblue.tristorage.platform.PlatformHooks;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//? if >=1.20.5 {
/*import com.andresblue.tristorage.platform.ChannelPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
*///?}
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class FabricPlatformHooks implements PlatformHooks {
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Optional<String> modName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getName());
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return FabricItemGroup.builder();
    }

    //? if >=1.20.5 {
    /*private final Map<ResourceLocation, ServerReceiver> receivers = new ConcurrentHashMap<>();

    public FabricPlatformHooks() {
        PayloadTypeRegistry.playC2S().register(ChannelPayload.TO_SERVER,
                ChannelPayload.codec(ChannelPayload.TO_SERVER));
        PayloadTypeRegistry.playS2C().register(ChannelPayload.TO_CLIENT,
                ChannelPayload.codec(ChannelPayload.TO_CLIENT));
        ServerPlayNetworking.registerGlobalReceiver(ChannelPayload.TO_SERVER, (payload, context) -> {
            ServerReceiver receiver = receivers.get(payload.channel());
            if (receiver != null) {
                receiver.receive(context.server(), context.player(), payload.buffer());
            }
        });
    }

    @Override
    public void registerServerReceiver(ResourceLocation id, ServerReceiver receiver) {
        receivers.put(id, receiver);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buffer) {
        ServerPlayNetworking.send(player, ChannelPayload.wrap(ChannelPayload.TO_CLIENT, id, buffer));
    }
    *///?} else {
    @Override
    public void registerServerReceiver(ResourceLocation id, ServerReceiver receiver) {
        ServerPlayNetworking.registerGlobalReceiver(id,
                (server, player, handler, buffer, sender) -> receiver.receive(server, player, buffer));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buffer) {
        ServerPlayNetworking.send(player, id, buffer);
    }
    //?}

    @Override
    public void onServerStarted(Consumer<MinecraftServer> listener) {
        ServerLifecycleEvents.SERVER_STARTED.register(listener::accept);
    }

    @Override
    public void onServerStopping(Consumer<MinecraftServer> listener) {
        ServerLifecycleEvents.SERVER_STOPPING.register(listener::accept);
    }

    @Override
    public void onServerTickEnd(Consumer<MinecraftServer> listener) {
        ServerTickEvents.END_SERVER_TICK.register(listener::accept);
    }

    @Override
    public void onLevelLoad(BiConsumer<MinecraftServer, ServerLevel> listener) {
        ServerWorldEvents.LOAD.register(listener::accept);
    }

    @Override
    public void onDataPackReloaded(Consumer<MinecraftServer> listener) {
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) {
                listener.accept(server);
            }
        });
    }

    @Override
    public void onPlayerDisconnect(Consumer<ServerPlayer> listener) {
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> listener.accept(handler.player));
    }

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> listener.accept(dispatcher));
    }
}
//?}
