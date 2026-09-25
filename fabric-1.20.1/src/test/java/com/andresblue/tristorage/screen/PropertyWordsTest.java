package com.andresblue.tristorage.screen;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.inventory.SimpleContainerData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PropertyWordsTest {
    private static final long[] VALUES = {
            0L, 1L, 32_767L, 32_768L, 40_000L, 65_535L, 65_536L, 70_000L,
            373_248L, 23_887_872L, Integer.MAX_VALUE, 1L << 40, Long.MAX_VALUE
    };

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void longValuesSurviveTheVanillaPropertyPacket() {
        for (long value : VALUES) {
            SimpleContainerData client = transmit(value, PropertyWords.LONG_WORDS);
            assertEquals(value, PropertyWords.read(client, 0, PropertyWords.LONG_WORDS),
                    "value " + value);
        }
    }

    @Test
    void intValuesSurviveTheVanillaPropertyPacket() {
        for (long value : VALUES) {
            if (value > Integer.MAX_VALUE) {
                continue;
            }
            SimpleContainerData client = transmit(value, PropertyWords.INT_WORDS);
            assertEquals(value, PropertyWords.read(client, 0, PropertyWords.INT_WORDS),
                    "value " + value);
        }
    }

    @Test
    void vanillaPacketTruncatesWideValues() {
        // Documents the original bug: a raw int property loses its upper bits.
        assertNotEquals(40_000, roundTrip(40_000));
    }

    private static SimpleContainerData transmit(long value, int words) {
        SimpleContainerData client = new SimpleContainerData(words);
        for (int index = 0; index < words; index++) {
            client.set(index, roundTrip(PropertyWords.word(value, index)));
        }
        return client;
    }

    private static int roundTrip(int value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new ClientboundContainerSetDataPacket(1, 0, value).write(buffer);
        return new ClientboundContainerSetDataPacket(buffer).getValue();
    }
}
