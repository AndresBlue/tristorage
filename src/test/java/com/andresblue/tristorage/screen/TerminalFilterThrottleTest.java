package com.andresblue.tristorage.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerminalFilterThrottleTest {
    private static final long MILLIS = 1_000_000L;

    @Test
    void cheapRebuildsAnswerWithinTwoTicks() {
        assertEquals(2, TerminalScreenHandler.filterCooldownTicks(0));
        assertEquals(2, TerminalScreenHandler.filterCooldownTicks(3 * MILLIS));
    }

    @Test
    void expensiveRebuildsAreSpacedByTheirCost() {
        // A 100 ms rebuild at 100k types may run about once every 8 ticks.
        assertEquals(8, TerminalScreenHandler.filterCooldownTicks(100 * MILLIS));
        assertEquals(40, TerminalScreenHandler.filterCooldownTicks(10_000 * MILLIS));
    }
}
