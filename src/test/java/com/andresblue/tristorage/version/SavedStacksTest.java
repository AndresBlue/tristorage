package com.andresblue.tristorage.version;

import com.andresblue.tristorage.TestStacks;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedStacksTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
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
}
