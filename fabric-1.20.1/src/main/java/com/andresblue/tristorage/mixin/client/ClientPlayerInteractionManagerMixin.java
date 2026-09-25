package com.andresblue.tristorage.mixin.client;

import com.andresblue.tristorage.client.TerminalScrollGuard;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPlayerInteractionManager.class, priority = 10000)
abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    private void tristorage$blockWheelInventoryTransfer(int syncId, int slotId, int button,
                                                         SlotActionType actionType,
                                                         PlayerEntity player, CallbackInfo ci) {
        if (TerminalScrollGuard.blocksInventoryClicks()) {
            ci.cancel();
        }
    }
}
