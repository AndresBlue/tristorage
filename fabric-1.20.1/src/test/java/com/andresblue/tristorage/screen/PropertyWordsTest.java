package com.andresblue.tristorage.screen;

import io.netty.buffer.Unpooled;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ScreenHandlerPropertyUpdateS2CPacket;
import net.minecraft.screen.ArrayPropertyDelegate;
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
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void longValuesSurviveTheVanillaPropertyPacket() {
        for (long value : VALUES) {
            ArrayPropertyDelegate client = transmit(value, PropertyWords.LONG_WORDS);
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
            ArrayPropertyDelegate client = transmit(value, PropertyWords.INT_WORDS);
            assertEquals(value, PropertyWords.read(client, 0, PropertyWords.INT_WORDS),
                    "value " + value);
        }
    }

    @Test
    void vanillaPacketTruncatesWideValues() {
        // Documents the original bug: a raw int property loses its upper bits.
        assertNotEquals(40_000, roundTrip(40_000));
    }

    private static ArrayPropertyDelegate transmit(long value, int words) {
        ArrayPropertyDelegate client = new ArrayPropertyDelegate(words);
        for (int index = 0; index < words; index++) {
            client.set(index, roundTrip(PropertyWords.word(value, index)));
        }
        return client;
    }

    private static int roundTrip(int value) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        new ScreenHandlerPropertyUpdateS2CPacket(1, 0, value).write(buffer);
        return new ScreenHandlerPropertyUpdateS2CPacket(buffer).getValue();
    }
}
