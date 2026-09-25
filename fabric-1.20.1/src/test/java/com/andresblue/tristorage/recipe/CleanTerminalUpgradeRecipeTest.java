package com.andresblue.tristorage.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;

class CleanTerminalUpgradeRecipeTest {
    @Test
    void acceptsOnlyNbtFreeIngredient() {
        assertTrue(TerminalUpgradeSafety.isClean(null));

        CompoundTag carryingData = new CompoundTag();
        carryingData.putString("TriStorageData", "must-not-be-consumed");
        assertFalse(TerminalUpgradeSafety.isClean(carryingData));
    }
}
