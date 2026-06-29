package io.github.serialdebug.core.parser;

import java.nio.charset.StandardCharsets;

public class AsciiParser implements DataParser {

    @Override
    public byte[] encode(String text) {
        if (text == null) {
            return new byte[0];
        }
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public String decode(byte[] data, int offset, int length) {
        if (data == null || length == 0) {
            return "";
        }
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            byte b = data[offset + i];
            chars[i] = (b >= 0x20 && b <= 0x7E) ? (char) b : '.';
        }
        return new String(chars);
    }
}