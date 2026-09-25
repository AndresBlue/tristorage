package com.andresblue.tristorage.screen;

import net.minecraft.item.ItemStack;

/**
 * Small, side-effect-free rules used by the crafting terminal's automatic
 * refill path. Keeping these decisions separate makes the vanilla remainder
 * behaviour explicit and independently testable.
 */
final class CraftingRefillPlanner {
    private CraftingRefillPlanner() {
    }

    static boolean shouldRefill(ItemStack beforeCraft, ItemStack afterCraft) {
        return beforeCraft != null && !beforeCraft.isEmpty()
                && (afterCraft == null || afterCraft.isEmpty());
    }

    static int quickMoveOutputLimit(ItemStack result) {
        return result == null || result.isEmpty() ? 0 : result.getMaxCount();
    }
}
