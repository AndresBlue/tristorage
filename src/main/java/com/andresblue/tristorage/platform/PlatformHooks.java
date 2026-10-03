package com.andresblue.tristorage.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
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
//? if <1.20 {
/*import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
*///?}

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Loader services used by common code on both physical sides. */
public interface PlatformHooks {
    Path configDir();

    /** Display name of an installed mod, used for search text and category labels. */
    Optional<String> modName(String modId);

    //? if >=1.20 {
    /** A creative tab builder the loader places in its own tab pages. */
    CreativeModeTab.Builder creativeTabBuilder();
    //?} else {
    /*// A creative tab that lists these items in order. Before 1.20 a tab is
    // created, not registered, and takes its place in the tab pages at once.
    CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon,
                                Supplier<List<ItemLike>> items);
    *///?}

    /** A block entity type for these blocks; vanilla builds them differently since 1.21.2. */
    <T extends BlockEntity> BlockEntityType<T> blockEntityType(
            BiFunction<BlockPos, BlockState, T> factory, Block... blocks);

    /**
     * Registers a client-to-server packet. The receiver runs on the network
     * thread; handlers hop to the server thread themselves.
     */
    void registerServerReceiver(ResourceLocation id, ServerReceiver receiver);

    void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buffer);

    void onServerStarted(Consumer<MinecraftServer> listener);

    /** Fires before players are saved and disconnected. */
    void onServerStopping(Consumer<MinecraftServer> listener);

    void onServerTickEnd(Consumer<MinecraftServer> listener);

    void onLevelLoad(BiConsumer<MinecraftServer, ServerLevel> listener);

    /** Fires after a successful data pack reload. */
    void onDataPackReloaded(Consumer<MinecraftServer> listener);

    void onPlayerDisconnect(Consumer<ServerPlayer> listener);

    void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener);

    @FunctionalInterface
    interface ServerReceiver {
        void receive(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buffer);
    }
}
