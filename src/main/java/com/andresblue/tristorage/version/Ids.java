package com.andresblue.tristorage.version;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
//? if >=1.19.3 {
import net.minecraft.core.registries.Registries;
//?} else {
/*import net.minecraft.core.Registry;
*///?}

/**
 * ResourceLocation factories; the public constructor became private in 1.21.
 * Registry keys moved to {@code Registries} in 1.19.3.
 */
public final class Ids {
    private Ids() {
    }

    public static ResourceLocation of(String namespace, String path) {
        //? if >=1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        return new ResourceLocation(namespace, path);
        //?}
    }

    public static ResourceLocation parse(String id) {
        //? if >=1.21 {
        /*return ResourceLocation.parse(id);
        *///?} else {
        return new ResourceLocation(id);
        //?}
    }

    public static ResourceKey<Level> dimension(ResourceLocation id) {
        //? if >=1.19.3 {
        return ResourceKey.create(Registries.DIMENSION, id);
        //?} else {
        /*return ResourceKey.create(Registry.DIMENSION_REGISTRY, id);
        *///?}
    }
}
