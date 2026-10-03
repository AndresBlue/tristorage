package com.andresblue.tristorage.storage;

import com.andresblue.tristorage.version.Nbt;

import com.andresblue.tristorage.TestStacks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
//? if >=1.21.11 {
/*import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.platform.Registrar;
import com.andresblue.tristorage.version.Stacks;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
*///?}

class PortableCoreDataTest {
    @BeforeAll
    static void bootstrap() {
        TestStacks.bootstrap();
    }

    @Test
    void sanitizedCopyKeepsOnlyStoragePayload() {
        CompoundTag raw = new CompoundTag();
        raw.putString("id", "tristorage:storage_core");
        raw.putInt("x", 12);
        raw.putInt(PortableCoreData.CHESTS_KEY, 7);
        ListTag entries = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putLong("Count", 123L);
        entries.add(entry);
        raw.put(PortableCoreData.ENTRIES_KEY, entries);

        CompoundTag portable = PortableCoreData.sanitizedCopy(raw);

        assertEquals(7, Nbt.getInt(portable, PortableCoreData.CHESTS_KEY));
        assertEquals(123L, Nbt.getLong(Nbt.getCompound(
                Nbt.getCompoundList(portable, PortableCoreData.ENTRIES_KEY), 0), "Count"));
        assertFalse(portable.contains("id"));
        assertFalse(portable.contains("x"));
        assertNotSame(entries, portable.get(PortableCoreData.ENTRIES_KEY));
    }

    @Test
    void fabricPortComponentIsRewrittenWithItsDataVersion() {
        CompoundTag stone = new CompoundTag();
        stone.putString("id", "minecraft:stone");
        stone.putInt("count", 1);
        CompoundTag stored = new CompoundTag();
        stored.put("stack", stone);
        stored.putLong("count", 640L);
        ListTag stacks = new ListTag();
        stacks.add(stored);
        CompoundTag component = new CompoundTag();
        component.putInt("installed_chests", 5);
        component.put("entries", stacks);

        CompoundTag carried = PortableCoreData.fromFabricPortComponent(component);

        assertEquals(5, Nbt.getInt(carried, PortableCoreData.CHESTS_KEY));
        CompoundTag first = Nbt.getCompound(
                Nbt.getCompoundList(carried, PortableCoreData.ENTRIES_KEY), 0);
        assertEquals(640L, Nbt.getLong(first, "Count"));
        assertEquals("minecraft:stone", Nbt.getString(Nbt.getCompound(first, "Stack"), "id"));
        assertEquals(4671, Nbt.getInt(carried, PortableCoreData.DATA_VERSION_KEY));
    }

    @Test
    void sanitizedCopyRejectsNegativeChestCountsAndUnrelatedData() {
        CompoundTag raw = new CompoundTag();
        raw.putInt(PortableCoreData.CHESTS_KEY, -42);
        raw.putString("Injected", "not portable");

        CompoundTag portable = PortableCoreData.sanitizedCopy(raw);

        assertEquals(0, Nbt.getInt(portable, PortableCoreData.CHESTS_KEY));
        assertFalse(portable.contains("Injected"));
        assertTrue(portable.contains(PortableCoreData.CHESTS_KEY));
    }

    @Test
    void sanitizedCopyKeepsTheDataVersionOfTheEntries() {
        CompoundTag raw = new CompoundTag();
        raw.putInt(PortableCoreData.DATA_VERSION_KEY, 3465);

        assertEquals(3465, Nbt.getInt(PortableCoreData.sanitizedCopy(raw),
                PortableCoreData.DATA_VERSION_KEY));
    }

    private static CompoundTag storage(int chests, long... counts) {
        CompoundTag storage = new CompoundTag();
        storage.putInt(PortableCoreData.CHESTS_KEY, chests);
        ListTag entries = new ListTag();
        for (long count : counts) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Count", count);
            entries.add(entry);
        }
        storage.put(PortableCoreData.ENTRIES_KEY, entries);
        return storage;
    }

    @Test
    void readsCoresFromTheNeoForgePortPayload() {
        // TriStorage 1.1 for NeoForge 1.21.1 kept the storage under TriStorageCore.
        ItemStack core = new ItemStack(Items.IRON_BLOCK);
        TestStacks.edit(core, tag -> tag.put("TriStorageCore", storage(3, 10, 5)));

        assertEquals(3, PortableCoreData.installedChests(core));
        assertEquals(2, PortableCoreData.storedTypes(core));
        assertEquals(15, PortableCoreData.totalItems(core));
        assertNotNull(PortableCoreData.copyFrom(core));
    }

    @Test
    void readsCoresFromTheFirstNeoForgePortLayout() {
        // TriStorage 1.0 for NeoForge 1.21.1 kept it at the root of custom data.
        ItemStack core = new ItemStack(Items.IRON_BLOCK);
        TestStacks.edit(core, tag -> tag.merge(storage(1, 7)));

        assertEquals(1, PortableCoreData.installedChests(core));
        assertEquals(7, PortableCoreData.totalItems(core));
    }

    //? if >=1.21.11 {
    /*// TriStorage 1.x for Fabric 1.21.11 kept the storage in a component of its own.
    @Test
    void readsCoresFromTheFabricPortComponent() {
        if (TriStorageMod.LEGACY_CORE_STORAGE == null) {
            TriStorageMod.registerDataComponents(new Registrar<>() {
                @Override
                public <V extends DataComponentType<?>> V register(String path, V value) {
                    return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                            TriStorageMod.id(path), value);
                }
            });
        }
        CompoundTag stone = new CompoundTag();
        stone.putString("id", "minecraft:stone");
        stone.putInt("count", 1);
        CompoundTag stored = new CompoundTag();
        stored.put("stack", stone);
        stored.putLong("count", 640L);
        ListTag stacks = new ListTag();
        stacks.add(stored);
        CompoundTag component = new CompoundTag();
        component.putInt("installed_chests", 5);
        component.put("entries", stacks);
        CompoundTag components = new CompoundTag();
        components.put("tristorage:core_storage", component);
        CompoundTag saved = new CompoundTag();
        saved.putString("id", "minecraft:iron_block");
        saved.putInt("count", 1);
        saved.put("components", components);

        ItemStack core = Stacks.parseStrict(saved, TestStacks.registries());

        assertNotNull(core);
        assertEquals(5, PortableCoreData.installedChests(core));
        assertEquals(1, PortableCoreData.storedTypes(core));
        assertEquals(640L, PortableCoreData.totalItems(core));
    }
    *///?}

    @Test
    void unrelatedCustomDataCarriesNoStorage() {
        ItemStack core = new ItemStack(Items.IRON_BLOCK);
        TestStacks.edit(core, tag -> tag.putString("Note", "hello"));

        assertEquals(0, PortableCoreData.installedChests(core));
        assertNull(PortableCoreData.copyFrom(core));
    }
}
