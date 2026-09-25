package com.andresblue.tristorage.block;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/** Direction occupied by the Linker's external dimensional antenna. */
public enum AntennaMount implements StringRepresentable {
    NONE("none", null),
    UP("up", Direction.UP),
    NORTH("north", Direction.NORTH),
    SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST),
    EAST("east", Direction.EAST),
    DOWN("down", Direction.DOWN);

    private static final List<AntennaMount> INSTALL_ORDER = List.of(
            UP, NORTH, SOUTH, WEST, EAST, DOWN
    );

    private final String name;
    private final Direction direction;

    AntennaMount(String name, Direction direction) {
        this.name = name;
        this.direction = direction;
    }

    public Direction direction() {
        return direction;
    }

    public static AntennaMount firstAvailable(Predicate<Direction> isAir) {
        for (AntennaMount mount : INSTALL_ORDER) {
            if (isAir.test(mount.direction)) {
                return mount;
            }
        }
        return NONE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
