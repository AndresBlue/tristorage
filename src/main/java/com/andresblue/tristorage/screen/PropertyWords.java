package com.andresblue.tristorage.screen;

import net.minecraft.world.inventory.ContainerData;

/**
 * Splits values across vanilla screen properties. The property packet carries
 * each value as a signed 16-bit short, so anything wider is sent as unsigned
 * 16-bit words and reassembled with a mask on the client. Integrated servers
 * skip packet encoding, which is why truncation only appeared on dedicated
 * and LAN servers.
 */
final class PropertyWords {
    static final int INT_WORDS = 2;
    static final int LONG_WORDS = 4;
    private static final int WORD_BITS = 16;
    private static final long WORD_MASK = 0xFFFFL;

    private PropertyWords() {
    }

    static int word(long value, int index) {
        return (int) ((value >>> (index * WORD_BITS)) & WORD_MASK);
    }

    static long read(ContainerData properties, int start, int words) {
        long value = 0;
        for (int index = 0; index < words; index++) {
            value |= (properties.get(start + index) & WORD_MASK) << (index * WORD_BITS);
        }
        return value;
    }
}
