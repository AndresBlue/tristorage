package com.andresblue.tristorage.version;

import com.andresblue.tristorage.TestStacks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedStacksTest {
    @BeforeAll
    static void bootstrap() {
        TestStacks.bootstrap();
    }

    /** A damaged, named sword as TriStorage for Minecraft 1.20.1 saved it. */
    private static CompoundTag legacySword() {
        CompoundTag display = new CompoundTag();
        display.putString("Name", "{\"text\":\"Old blade\"}");
        CompoundTag tag = new CompoundTag();
        tag.putInt("Damage", 7);
        tag.put("display", display);
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "minecraft:diamond_sword");
        stack.putByte("Count", (byte) 1);
        stack.put("tag", tag);
        return stack;
    }

    @Test
    void legacyStackWithoutRecordedVersionKeepsItsData() {
        ItemStack parsed = SavedStacks.parse(legacySword(), 0, TestStacks.registries());

        assertTrue(parsed.is(Items.DIAMOND_SWORD));
        assertEquals(7, parsed.getDamageValue());
        assertEquals("Old blade", parsed.getHoverName().getString());
    }

    @Test
    void recordedVersionIsUpgradedFrom() {
        ItemStack parsed = SavedStacks.parse(legacySword(), 3465, TestStacks.registries());

        assertEquals(7, parsed.getDamageValue());
    }

    @Test
    void currentStacksPassThroughUnchanged() {
        ItemStack stone = new ItemStack(Items.STONE, 12);
        CompoundTag saved = Stacks.save(stone, TestStacks.registries());

        ItemStack parsed = SavedStacks.parse(saved, SavedStacks.currentVersion(),
                TestStacks.registries());
        assertTrue(parsed.is(Items.STONE));
        assertEquals(12, parsed.getCount());
        assertEquals(saved, SavedStacks.upgrade(saved, 0));
    }

    //? if >=1.21 {
    /*// A damaged, named, enchanted sword as a 1.x port saved it. Until 1.21.5
    // text was a JSON string and enchantment levels sat under "levels".
    private static CompoundTag portSword(boolean since1215) {
        CompoundTag components = new CompoundTag();
        components.putInt("minecraft:damage", 7);
        CompoundTag enchantments = new CompoundTag();
        if (since1215) {
            enchantments.putInt("minecraft:sharpness", 3);
            components.putString("minecraft:custom_name", "Old blade");
        } else {
            CompoundTag levels = new CompoundTag();
            levels.putInt("minecraft:sharpness", 3);
            enchantments.put("levels", levels);
            components.putString("minecraft:custom_name", "{\"text\":\"Old blade\"}");
        }
        components.put("minecraft:enchantments", enchantments);
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "minecraft:diamond_sword");
        stack.putInt("count", 1);
        stack.put("components", components);
        return stack;
    }

    private static void assertPortSword(ItemStack parsed) {
        assertTrue(parsed.is(Items.DIAMOND_SWORD));
        assertEquals(7, parsed.getDamageValue());
        assertEquals("Old blade", parsed.getHoverName().getString());
        assertEquals(1, parsed.getEnchantments().size());
        assertEquals(3, parsed.getEnchantments().entrySet().iterator().next().getIntValue());
    }

    @Test
    void unrecordedStackFromThe1211PortsIsUpgraded() {
        assertPortSword(SavedStacks.parse(portSword(false), 0, TestStacks.dataRegistries()));
    }
    *///?}

    //? if >=1.21.11 {
    /*// The Fabric 1.21.11 port wrote the current layout; fixing it again from
    // 1.21.1 would drop the enchantment.
    @Test
    void unrecordedStackFromThe12111PortIsNotFixedAgain() {
        assertPortSword(SavedStacks.parse(portSword(true), 0, TestStacks.dataRegistries()));
    }
    *///?}
}
