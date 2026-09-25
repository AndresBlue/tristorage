package com.andresblue.tristorage.mixin.client;

import com.andresblue.tristorage.client.TerminalScrollGuard;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 10000)
abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"))
    private void tristorage$beginTerminalScroll(long window, double horizontal,
                                                double vertical, CallbackInfo ci) {
        TerminalScrollGuard.begin();
    }
}
