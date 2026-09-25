package com.andresblue.tristorage.recipe;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public final class TerminalUpgradeSafety {
    private TerminalUpgradeSafety() {
    }

    public static boolean isClean(@Nullable CompoundTag nbt) {
        return nbt == null;
    }
}
