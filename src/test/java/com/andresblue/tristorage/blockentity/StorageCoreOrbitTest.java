package com.andresblue.tristorage.blockentity;

import com.andresblue.tristorage.TestStacks;
import com.andresblue.tristorage.version.Stacks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
//? if >=1.20.5 {
/*import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WritableBookContent;
*///?} else {
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
//?}

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageCoreOrbitTest {
    @BeforeAll
    static void bootstrap() {
        TestStacks.bootstrap();
    }

    @Test
    void containerContentsNeverReachTheOrbitPacket() {
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        //? if >=1.20.5 {
        /*shulker.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
        *///?} else {
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.put("Items", new ListTag());
        TestStacks.edit(shulker, tag -> tag.put("BlockEntityTag", blockEntity));
        //?}

        ItemStack shown = StorageCoreBlockEntity.orbitDisplayStack(shulker);

        assertTrue(shown.is(Items.SHULKER_BOX));
        assertNull(Stacks.dataIdentity(shown, false));
    }

    @Test
    void bookPagesAreStrippedButEnchantmentGlintIsKept() {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        //? if >=1.20.5 {
        /*book.set(DataComponents.WRITABLE_BOOK_CONTENT,
                new WritableBookContent(List.of(Filterable.passThrough("secret"))));
        *///?} else {
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf("secret"));
        TestStacks.edit(book, tag -> tag.put("pages", pages));
        //?}
        assertNull(Stacks.dataIdentity(StorageCoreBlockEntity.orbitDisplayStack(book), false));

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        //? if >=1.20.5 {
        /*// Enchantments are data-driven since 1.21; the glint override stands in for them.
        sword.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        *///?} else {
        ListTag enchantments = new ListTag();
        CompoundTag sharpness = new CompoundTag();
        sharpness.putString("id", "minecraft:sharpness");
        sharpness.putShort("lvl", (short) 5);
        enchantments.add(sharpness);
        TestStacks.edit(sword, tag -> tag.put("Enchantments", enchantments));
        //?}
        TestStacks.edit(sword, tag -> tag.putString("Private", "notes"));

        ItemStack shown = StorageCoreBlockEntity.orbitDisplayStack(sword);
        assertTrue(shown.hasFoil());
        assertFalse(TestStacks.data(shown).contains("Private"));
    }

    @Test
    void dyeColorIsKeptWithoutNameOrLore() {
        ItemStack armor = new ItemStack(Items.LEATHER_CHESTPLATE);
        //? if >=1.20.5 {
        /*//? if >=1.21.5 {
        /^armor.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366FF));
        ^///?} else {
        armor.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366FF, true));
        //?}
        armor.set(DataComponents.CUSTOM_NAME, Component.literal("Base keys"));

        ItemStack shown = StorageCoreBlockEntity.orbitDisplayStack(armor);
        assertEquals(0x3366FF, shown.get(DataComponents.DYED_COLOR).rgb());
        assertFalse(shown.has(DataComponents.CUSTOM_NAME));
        *///?} else {
        TestStacks.edit(armor, tag -> {
            CompoundTag display = new CompoundTag();
            display.putInt("color", 0x3366FF);
            display.putString("Name", "{\"text\":\"Base keys\"}");
            tag.put("display", display);
        });

        CompoundTag shown = TestStacks.data(StorageCoreBlockEntity.orbitDisplayStack(armor))
                .getCompound("display");
        assertEquals(0x3366FF, shown.getInt("color"));
        assertFalse(shown.contains("Name"));
        //?}
    }
}
