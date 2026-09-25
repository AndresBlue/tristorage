package com.andresblue.tristorage.recipe;

import com.andresblue.tristorage.version.Stacks;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class TerminalUpgradeSafety {
    private TerminalUpgradeSafety() {
    }

    /** A terminal is clean when it carries no data at all, not even a name. */
    public static boolean isClean(ItemStack terminal) {
        return isCleanData(Stacks.dataIdentity(terminal, false));
    }

    static boolean isCleanData(@Nullable Object data) {
        return data == null;
    }
}
