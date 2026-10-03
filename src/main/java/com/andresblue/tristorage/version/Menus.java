package com.andresblue.tristorage.version;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
//? if >=1.20 {
import net.minecraft.world.inventory.TransientCraftingContainer;
//?}

/** Container menu calls that changed between Minecraft versions. */
public final class Menus {
    private Menus() {
    }

    /** A crafting grid owned by a menu. The class became an interface in 1.20. */
    public static CraftingContainer craftingGrid(AbstractContainerMenu menu, int width, int height) {
        //? if >=1.20 {
        return new TransientCraftingContainer(menu, width, height);
        //?} else {
        /*return new CraftingContainer(menu, width, height);
        *///?}
    }

    /** Sets a slot's stack as a player's action, which slots tell apart since 1.19.4. */
    public static void setByPlayer(Slot slot, ItemStack stack) {
        //? if >=1.19.4 {
        slot.setByPlayer(stack);
        //?} else {
        /*slot.set(stack);
        *///?}
    }
}
