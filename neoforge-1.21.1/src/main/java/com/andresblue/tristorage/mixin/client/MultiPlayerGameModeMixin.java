package com.andresblue.tristorage.mixin.client;

import com.andresblue.tristorage.client.TerminalScrollGuard;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MultiPlayerGameMode.class, priority = 10000)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void tristorage$blockWheelInventoryTransfer(int syncId, int slotId, int button,
                                                        ClickType actionType,
                                                        Player player, CallbackInfo ci) {
        if (TerminalScrollGuard.blocksInventoryClicks()) {
            ci.cancel();
        }
    }
}
