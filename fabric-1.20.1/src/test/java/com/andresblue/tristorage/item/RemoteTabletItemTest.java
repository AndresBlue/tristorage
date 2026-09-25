package com.andresblue.tristorage.item;

import com.andresblue.tristorage.TriStorageMod;
import com.andresblue.tristorage.storage.RemoteTerminalMode;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteTabletItemTest {
    private static final BlockPos LINKER = new BlockPos(12, 64, -9);

    private static RegistryKey<World> dimension() {
        return RegistryKey.of(RegistryKeys.WORLD,
                new Identifier("minecraft", "overworld"));
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void upgradeCopiesOnlyValidatedLinkData() {
        ItemStack source = new ItemStack(TriStorageMod.REMOTE_TABLET);
        NbtCompound sourceNbt = source.getOrCreateNbt();
        sourceNbt.putString("LinkedDimension", "minecraft:overworld");
        sourceNbt.putLong("LinkedPosition", LINKER.asLong());
        sourceNbt.putLong("LinkedCorePosition", new BlockPos(13, 64, -9).asLong());
        sourceNbt.putString("InjectedData", "must-not-survive");

        ItemStack result = new ItemStack(TriStorageMod.WIRELESS_CRAFTING_TERMINAL);
        assertTrue(RemoteTabletItem.copyValidatedLink(source, result));
        assertTrue(RemoteTabletItem.isLinkedTo(
                result, dimension(), LINKER, RemoteTerminalMode.CRAFTING));
        assertFalse(RemoteTabletItem.isLinkedTo(
                result, dimension(), LINKER, RemoteTerminalMode.STORAGE));
        assertEquals("minecraft:overworld",
                result.getNbt().getString("LinkedDimension"));
        assertFalse(result.getNbt().contains("InjectedData"));
    }

    @Test
    void malformedLinkProducesCleanUpgrade() {
        ItemStack source = new ItemStack(TriStorageMod.REMOTE_TABLET);
        NbtCompound sourceNbt = source.getOrCreateNbt();
        sourceNbt.putString("LinkedDimension", "not an identifier");
        sourceNbt.putLong("LinkedPosition", LINKER.asLong());

        ItemStack result = new ItemStack(TriStorageMod.WIRELESS_CRAFTING_TERMINAL);
        assertFalse(RemoteTabletItem.copyValidatedLink(source, result));
        assertNull(result.getNbt());
    }

    @Test
    void optionalCoreHintMustBeALong() {
        NbtCompound malformed = new NbtCompound();
        malformed.putString("LinkedDimension", "minecraft:overworld");
        malformed.putLong("LinkedPosition", LINKER.asLong());
        malformed.putString("LinkedCorePosition", "wrong-type");
        assertFalse(RemoteTabletItem.hasValidLink(malformed));
    }

    @Test
    void tabletCapabilitiesAreExplicitAndCannotSubstituteEachOther() {
        assertEquals(RemoteTerminalMode.STORAGE,
                TriStorageMod.REMOTE_TABLET.mode());
        assertEquals(RemoteTerminalMode.CRAFTING,
                TriStorageMod.WIRELESS_CRAFTING_TERMINAL.mode());
    }
}
