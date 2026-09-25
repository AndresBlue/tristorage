package com.andresblue.tristorage.blockentity;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageCoreOrbitTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void containerContentsNeverReachTheOrbitPacket() {
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        NbtCompound blockEntity = new NbtCompound();
        blockEntity.put("Items", new NbtList());
        shulker.getOrCreateNbt().put("BlockEntityTag", blockEntity);

        ItemStack shown = StorageCoreBlockEntity.orbitDisplayStack(shulker);

        assertTrue(shown.isOf(Items.SHULKER_BOX));
        assertNull(shown.getNbt());
    }

    @Test
    void bookPagesAreStrippedButEnchantmentGlintIsKept() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtList pages = new NbtList();
        pages.add(NbtString.of("secret"));
        book.getOrCreateNbt().put("pages", pages);
        assertNull(StorageCoreBlockEntity.orbitDisplayStack(book).getNbt());

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        NbtList enchantments = new NbtList();
        NbtCompound sharpness = new NbtCompound();
        sharpness.putString("id", "minecraft:sharpness");
        sharpness.putShort("lvl", (short) 5);
        enchantments.add(sharpness);
        sword.getOrCreateNbt().put("Enchantments", enchantments);
        sword.getOrCreateNbt().putString("Private", "notes");

        NbtCompound shown = StorageCoreBlockEntity.orbitDisplayStack(sword).getNbt();
        assertTrue(shown.contains("Enchantments"));
        assertFalse(shown.contains("Private"));
    }

    @Test
    void dyeColorIsKeptWithoutNameOrLore() {
        ItemStack armor = new ItemStack(Items.LEATHER_CHESTPLATE);
        NbtCompound display = armor.getOrCreateSubNbt("display");
        display.putInt("color", 0x3366FF);
        display.putString("Name", "{\"text\":\"Base keys\"}");

        NbtCompound shown = StorageCoreBlockEntity.orbitDisplayStack(armor)
                .getSubNbt("display");
        assertEquals(0x3366FF, shown.getInt("color"));
        assertFalse(shown.contains("Name"));
    }
}
