package com.andresblue.tristorage.mixin.client;

import com.andresblue.tristorage.client.TerminalScrollGuard;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after normal-priority compatibility mixins have handled the wheel. */
@Mixin(value = MouseHandler.class, priority = 1)
abstract class MouseScrollEndMixin {
    @Inject(method = "onScroll", at = @At("RETURN"))
    private void tristorage$endTerminalScroll(long window, double horizontal,
                                              double vertical, CallbackInfo ci) {
        TerminalScrollGuard.end();
    }
}
