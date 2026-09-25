package com.andresblue.tristorage.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

import java.util.function.Predicate;

public enum AntennaMount implements StringRepresentable {
    NONE("none", null), UP("up", Direction.UP), NORTH("north", Direction.NORTH),
    SOUTH("south", Direction.SOUTH), WEST("west", Direction.WEST), EAST("east", Direction.EAST),
    DOWN("down", Direction.DOWN);

    private final String name;
    private final Direction direction;

    AntennaMount(String name, Direction direction) {
        this.name = name;
        this.direction = direction;
    }

    @Override public String getSerializedName() { return name; }
    public Direction direction() { return direction; }

    public static AntennaMount firstAvailable(Predicate<Direction> isAir) {
        for (AntennaMount mount : values()) {
            if (mount.direction != null && isAir.test(mount.direction)) return mount;
        }
        return NONE;
    }
}
