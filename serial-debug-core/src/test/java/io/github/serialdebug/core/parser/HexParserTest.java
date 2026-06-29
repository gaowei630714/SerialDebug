package io.github.serialdebug.core.parser;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HexParserTest {

    private final HexParser parser = new HexParser();

    @Test
    void shouldEncodeSimpleHex() {
        assertArrayEquals(new byte[]{0x01, 0x02, (byte) 0xFF},
                parser.encode("01 02 FF"));
    }

    @Test
    void shouldEncodeHexWithoutSpaces() {
        assertArrayEquals(new byte[]{0x01, 0x02, 0x0A},
                parser.encode("01020A"));
    }

    @Test
    void shouldEncodeEmptyString() {
        assertEquals(0, parser.encode("").length);
    }

    @Test
    void shouldEncodeHexWithLowercase() {
        assertArrayEquals(new byte[]{(byte) 0xAB, (byte) 0xCD, (byte) 0xEF},
                parser.encode("ab cd ef"));
    }

    @Test
    void shouldRejectOddLengthHex() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.encode("ABC"));
    }

    @Test
    void shouldRejectInvalidHexChars() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.encode("XX YY"));
    }

    @Test
    void shouldDecodeBytesToHexString() {
        assertEquals("01 02 FF",
                parser.decode(new byte[]{0x01, 0x02, (byte) 0xFF}, 0, 3));
    }

    @Test
    void shouldDecodeWithOffset() {
        byte[] data = {0x00, 0x01, 0x02, 0x03};
        assertEquals("01 02",
                parser.decode(data, 1, 2));
    }

    @Test
    void shouldDecodeEmptyArray() {
        assertEquals("", parser.decode(new byte[0], 0, 0));
    }
}