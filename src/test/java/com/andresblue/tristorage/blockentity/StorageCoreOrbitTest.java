package com.andresblue.tristorage.blockentity;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageCoreOrbitTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void containerContentsNeverReachTheOrbitPacket() {
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.put("Items", new ListTag());
        shulker.getOrCreateTag().put("BlockEntityTag", blockEntity);

        ItemStack shown = StorageCoreBlockEntity.orbitDisplayStack(shulker);

        assertTrue(shown.is(Items.SHULKER_BOX));
        assertNull(shown.getTag());
    }

    @Test
    void bookPagesAreStrippedButEnchantmentGlintIsKept() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf("secret"));
        book.getOrCreateTag().put("pages", pages);
        assertNull(StorageCoreBlockEntity.orbitDisplayStack(book).getTag());

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ListTag enchantments = new ListTag();
        CompoundTag sharpness = new CompoundTag();
        sharpness.putString("id", "minecraft:sharpness");
        sharpness.putShort("lvl", (short) 5);
        enchantments.add(sharpness);
        sword.getOrCreateTag().put("Enchantments", enchantments);
        sword.getOrCreateTag().putString("Private", "notes");

        CompoundTag shown = StorageCoreBlockEntity.orbitDisplayStack(sword).getTag();
        assertTrue(shown.contains("Enchantments"));
        assertFalse(shown.contains("Private"));
    }

    @Test
    void dyeColorIsKeptWithoutNameOrLore() {
        ItemStack armor = new ItemStack(Items.LEATHER_CHESTPLATE);
        CompoundTag display = armor.getOrCreateTagElement("display");
        display.putInt("color", 0x3366FF);
        display.putString("Name", "{\"text\":\"Base keys\"}");

        CompoundTag shown = StorageCoreBlockEntity.orbitDisplayStack(armor)
                .getTagElement("display");
        assertEquals(0x3366FF, shown.getInt("color"));
        assertFalse(shown.contains("Name"));
    }
}
