package com.andresblue.tristorage.block;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import net.minecraft.core.Direction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AntennaMountTest {
    @Test
    void prioritizesUpwardAir() {
        assertEquals(AntennaMount.UP,
                AntennaMount.firstAvailable(direction -> true));
    }

    @Test
    void fallsBackAroundObstructionsInStableOrder() {
        EnumSet<Direction> air = EnumSet.of(Direction.WEST, Direction.DOWN);
        assertEquals(AntennaMount.WEST,
                AntennaMount.firstAvailable(air::contains));
    }

    @Test
    void reportsNoneWhenCompletelyObstructed() {
        assertEquals(AntennaMount.NONE,
                AntennaMount.firstAvailable(direction -> false));
    }
}
