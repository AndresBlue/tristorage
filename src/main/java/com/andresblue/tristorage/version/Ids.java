package com.andresblue.tristorage.version;

import net.minecraft.resources.ResourceLocation;

/** ResourceLocation factories; the public constructor became private in 1.21. */
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
}
