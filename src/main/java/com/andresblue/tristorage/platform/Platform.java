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

/** Holds the loader's {@link PlatformHooks}. The loader entrypoint installs them first. */
public final class Platform {
    private static PlatformHooks hooks = new Uninstalled();

    private Platform() {
    }

    public static void install(PlatformHooks installed) {
        hooks = installed;
    }

    public static PlatformHooks get() {
        return hooks;
    }

    /**
     * Stand-in before a loader installs its hooks, which only happens in unit
     * tests. Queries answer neutrally; registering listeners or packets fails.
     */
    private static final class Uninstalled implements PlatformHooks {
        @Override
        public Path configDir() {
            return Path.of("config");
        }

        @Override
        public Optional<String> modName(String modId) {
            return Optional.empty();
        }

        //? if >=1.20 {
        @Override
        public CreativeModeTab.Builder creativeTabBuilder() {
            throw missing();
        }
        //?} else {
        /*@Override
        public CreativeModeTab creativeTab(ResourceLocation id, Supplier<ItemStack> icon,
                                           Supplier<List<ItemLike>> items) {
            throw missing();
        }
        *///?}

        @Override
        public <T extends BlockEntity> BlockEntityType<T> blockEntityType(
                BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
            throw missing();
        }

        @Override
        public void registerServerReceiver(ResourceLocation id, ServerReceiver receiver) {
            throw missing();
        }

        @Override
        public void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buffer) {
            throw missing();
        }

        @Override
        public void onServerStarted(Consumer<MinecraftServer> listener) {
            throw missing();
        }

        @Override
        public void onServerStopping(Consumer<MinecraftServer> listener) {
            throw missing();
        }

        @Override
        public void onServerTickEnd(Consumer<MinecraftServer> listener) {
            throw missing();
        }

        @Override
        public void onLevelLoad(BiConsumer<MinecraftServer, ServerLevel> listener) {
            throw missing();
        }

        @Override
        public void onDataPackReloaded(Consumer<MinecraftServer> listener) {
            throw missing();
        }

        @Override
        public void onPlayerDisconnect(Consumer<ServerPlayer> listener) {
            throw missing();
        }

        @Override
        public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
            throw missing();
        }

        private static IllegalStateException missing() {
            return new IllegalStateException("TriStorage platform hooks are not installed");
        }
    }
}
