package com.andresblue.tristorage.recipe;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

public final class TerminalUpgradeSafety {
    private TerminalUpgradeSafety() {
    }

    public static boolean isClean(@Nullable NbtCompound nbt) {
        return nbt == null;
    }
}
