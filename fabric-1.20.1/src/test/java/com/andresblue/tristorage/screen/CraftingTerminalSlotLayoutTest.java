package com.andresblue.tristorage.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftingTerminalSlotLayoutTest {
    @Test
    void virtualPlayerCraftingAndResultRangesNeverOverlap() {
        assertEquals(54, TerminalScreenHandler.PAGE_SIZE);
        assertEquals(54, TerminalScreenHandler.PLAYER_START);
        assertEquals(90, TerminalScreenHandler.PLAYER_END);
        assertEquals(90, CraftingTerminalScreenHandler.CRAFT_INPUT_START);
        assertEquals(99, CraftingTerminalScreenHandler.CRAFT_INPUT_END);
        assertEquals(99, CraftingTerminalScreenHandler.RESULT_SLOT);
    }
}
