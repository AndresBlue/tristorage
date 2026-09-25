package com.andresblue.tristorage.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminalFilterTest {
    @Test
    void searchIsCaseAndAccentInsensitive() {
        assertEquals("espada cosmica", TerminalFilter.normalize("  Espada Cósmica  "));
        assertTrue(TerminalFilter.matchesQuery(
                "modid espada cosmica legendaria", "espada cosmica"));
        assertFalse(TerminalFilter.matchesQuery(
                "modid espada cosmica", "espada blaze"));
    }

    @Test
    void malformedNetworkValuesFallBackSafely() {
        assertEquals(TerminalFilter.CategoryMode.NONE,
                TerminalFilter.CategoryMode.byNetworkId(-1));
        assertEquals(TerminalFilter.CategoryMode.NONE,
                TerminalFilter.CategoryMode.byNetworkId(999));
        TerminalFilter.Selection selection = TerminalFilter.sanitize(
                "stone", null, "blocks");
        assertEquals(TerminalFilter.CategoryMode.NONE, selection.mode());
        assertEquals(TerminalFilter.ALL, selection.category());
    }
}
