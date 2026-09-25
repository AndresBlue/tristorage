package com.andresblue.tristorage.platform.forge;

//? if forge {
/*import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.platform.PlatformHooks;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import java.util.function.BiFunction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ForgePlatformHooks implements PlatformHooks {
    // Every TriStorage packet shares one channel; each payload starts with its own id.
    static final ResourceLocation CHANNEL = TriStorageMod.id("net");
    private static final String PROTOCOL = "1";
    static final EventNetworkChannel NETWORK = NetworkRegistry.ChannelBuilder.named(CHANNEL)
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .eventNetworkChannel();
    // Set by the client hooks; the dedicated server never receives client-bound payloads.
    static volatile BiConsumer<ResourceLocation, FriendlyByteBuf> clientDispatcher;

    private final Map<ResourceLocation, ServerReceiver> serverReceivers = new ConcurrentHashMap<>();

    public ForgePlatformHooks() {
        NETWORK.registerObject(this);
    }

    // Channel registration and login events share this bus but carry no payload.
    @SubscribeEvent
    public void onPayload(NetworkEvent event) {
        FriendlyByteBuf payload = event.getPayload();
        if (payload == null) {
            return;
        }
        NetworkEvent.Context context = event.getSource().get();
        context.setPacketHandled(true);
        ResourceLocation id = payload.readResourceLocation();
        if (context.getDirection().getReceptionSide().isServer()) {
            ServerPlayer player = context.getSender();
            ServerReceiver receiver = serverReceivers.get(id);
            if (player != null && receiver != null) {
                receiver.receive(player.getServer(), player, payload);
            }
        } else if (clientDispatcher != null) {
            clientDispatcher.accept(id, payload);
        }
    }

    static FriendlyByteBuf frame(ResourceLocation id, FriendlyByteBuf buffer) {
        FriendlyByteBuf framed = new FriendlyByteBuf(Unpooled.buffer());
        framed.writeResourceLocation(id);
        framed.writeBytes(buffer);
        return framed;
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
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(
            BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory::apply, blocks).build(null);
    }

    @Override
    public void registerServerReceiver(ResourceLocation id, ServerReceiver receiver) {
        serverReceivers.put(id, receiver);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buffer) {
        player.connection.send(new ClientboundCustomPayloadPacket(CHANNEL, frame(id, buffer)));
    }

    @Override
    public void onServerStarted(Consumer<MinecraftServer> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                ServerStartedEvent.class, event -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerStopping(Consumer<MinecraftServer> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                ServerStoppingEvent.class, event -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerTickEnd(Consumer<MinecraftServer> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                TickEvent.ServerTickEvent.class, event -> {
                    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                    if (event.phase == TickEvent.Phase.END && server != null) {
                        listener.accept(server);
                    }
                });
    }

    @Override
    public void onLevelLoad(BiConsumer<MinecraftServer, ServerLevel> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                LevelEvent.Load.class, event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        listener.accept(level.getServer(), level);
                    }
                });
    }

    @Override
    public void onDataPackReloaded(Consumer<MinecraftServer> listener) {
        // Without a player the sync follows a completed /reload.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                OnDatapackSyncEvent.class, event -> {
                    if (event.getPlayer() == null) {
                        listener.accept(event.getPlayerList().getServer());
                    }
                });
    }

    @Override
    public void onPlayerDisconnect(Consumer<ServerPlayer> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                PlayerEvent.PlayerLoggedOutEvent.class, event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        listener.accept(player);
                    }
                });
    }

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                RegisterCommandsEvent.class, event -> listener.accept(event.getDispatcher()));
    }
}
*///?}
