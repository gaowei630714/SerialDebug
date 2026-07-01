package io.github.serialdebug.core.parser;

import java.util.HexFormat;

public class HexParser implements DataParser {

    @Override
    public byte[] encode(String text) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        String cleaned = text.replaceAll("\\s+", "");
        if (cleaned.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Hex string must have even number of characters: " + cleaned);
        }
        try {
            return HexFormat.of().parseHex(cleaned);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid hex string: " + text, e);
        }
    }

    @Override
    public String decode(byte[] data, int offset, int length) {
        if (data == null || length == 0) {
            return "";
        }
        return HexFormat.of().withUpperCase().withDelimiter(" ")
                .formatHex(data, offset, offset + length);
    }
}