package io.github.serialdebug.core.parser;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AsciiParserTest {

    private final AsciiParser parser = new AsciiParser();

    @Test
    void shouldEncodeAsciiString() {
        assertArrayEquals("hello".getBytes(StandardCharsets.US_ASCII),
                parser.encode("hello"));
    }

    @Test
    void shouldEncodeEmptyString() {
        assertEquals(0, parser.encode("").length);
    }

    @Test
    void shouldEncodeWithSpecialCharacters() {
        assertArrayEquals(new byte[]{'A', 'T', '\r', '\n'},
                parser.encode("AT\r\n"));
    }

    @Test
    void shouldDecodeBytesToAsciiString() {
        assertEquals("hello",
                parser.decode("hello".getBytes(StandardCharsets.US_ASCII), 0, 5));
    }

    @Test
    void shouldDecodeWithOffset() {
        byte[] data = "xxhelloyy".getBytes(StandardCharsets.US_ASCII);
        assertEquals("hello",
                parser.decode(data, 2, 5));
    }

    @Test
    void shouldDecodeEmptyArray() {
        assertEquals("", parser.decode(new byte[0], 0, 0));
    }

    @Test
    void shouldDecodeNonPrintableCharsAsDot() {
        byte[] data = {0x48, 0x65, 0x00, 0x6C, 0x6F};
        assertEquals("He.lo", parser.decode(data, 0, 5));
    }

    @Test
    void shouldEncodeEscapeSequences() {
        assertArrayEquals(new byte[]{'A', 'T', '+', '\r', '\n'},
                parser.encode("AT+\r\n"));
    }
}