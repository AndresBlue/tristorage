package com.andresblue.tristorage.platform.neoforge;

//? if neoforge {
/*import com.andresblue.tristorage.platform.ChannelPayload;
import com.andresblue.tristorage.platform.PlatformHooks;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class NeoForgePlatformHooks implements PlatformHooks {
    private static final String PROTOCOL = "1";
    // Set by the client hooks; the dedicated server never receives client-bound payloads.
    static volatile BiConsumer<ResourceLocation, FriendlyByteBuf> clientDispatcher;

    private final Map<ResourceLocation, ServerReceiver> serverReceivers = new ConcurrentHashMap<>();

    NeoForgePlatformHooks(IEventBus modBus) {
        modBus.addListener(EventPriority.NORMAL, false, RegisterPayloadHandlersEvent.class,
                this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(ChannelPayload.TO_SERVER,
                ChannelPayload.codec(ChannelPayload.TO_SERVER), (payload, context) -> {
                    ServerReceiver receiver = serverReceivers.get(payload.channel());
                    if (receiver != null && context.player() instanceof ServerPlayer player) {
                        receiver.receive(player.getServer(), player, payload.buffer());
                    }
                });
        registrar.playToClient(ChannelPayload.TO_CLIENT,
                ChannelPayload.codec(ChannelPayload.TO_CLIENT), (payload, context) -> {
                    BiConsumer<ResourceLocation, FriendlyByteBuf> dispatcher = clientDispatcher;
                    if (dispatcher != null) {
                        dispatcher.accept(payload.channel(), payload.buffer());
                    }
                });
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Optional<String> modName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName());
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public void registerServerReceiver(ResourceLocation id, ServerReceiver receiver) {
        serverReceivers.put(id, receiver);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buffer) {
        PacketDistributor.sendToPlayer(player,
                ChannelPayload.wrap(ChannelPayload.TO_CLIENT, id, buffer));
    }

    @Override
    public void onServerStarted(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                ServerStartedEvent.class, event -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerStopping(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                ServerStoppingEvent.class, event -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerTickEnd(Consumer<MinecraftServer> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                ServerTickEvent.Post.class, event -> listener.accept(event.getServer()));
    }

    @Override
    public void onLevelLoad(BiConsumer<MinecraftServer, ServerLevel> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                LevelEvent.Load.class, event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        listener.accept(level.getServer(), level);
                    }
                });
    }

    @Override
    public void onDataPackReloaded(Consumer<MinecraftServer> listener) {
        // Without a player the sync follows a completed /reload.
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                OnDatapackSyncEvent.class, event -> {
                    if (event.getPlayer() == null) {
                        listener.accept(event.getPlayerList().getServer());
                    }
                });
    }

    @Override
    public void onPlayerDisconnect(Consumer<ServerPlayer> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                PlayerEvent.PlayerLoggedOutEvent.class, event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        listener.accept(player);
                    }
                });
    }

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                RegisterCommandsEvent.class, event -> listener.accept(event.getDispatcher()));
    }
}
*///?}
