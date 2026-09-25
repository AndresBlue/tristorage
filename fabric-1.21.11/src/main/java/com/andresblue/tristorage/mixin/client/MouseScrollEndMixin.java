package com.andresblue.tristorage.mixin.client;

import com.andresblue.tristorage.client.TerminalScrollGuard;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Mouse.class, priority = 1)
abstract class MouseScrollEndMixin {
    @Inject(method = "onMouseScroll", at = @At("RETURN"))
    private void tristorage$endTerminalScroll(long window, double horizontal, double vertical,
                                              CallbackInfo ci) {
        TerminalScrollGuard.end();
    }
}
