package com.andresblue.tristorage.recipe;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanTerminalUpgradeRecipeTest {
    @Test
    void acceptsOnlyNbtFreeIngredient() {
        assertTrue(TerminalUpgradeSafety.isClean(null));

        NbtCompound carryingData = new NbtCompound();
        carryingData.putString("TriStorageData", "must-not-be-consumed");
        assertFalse(TerminalUpgradeSafety.isClean(carryingData));
    }
}
