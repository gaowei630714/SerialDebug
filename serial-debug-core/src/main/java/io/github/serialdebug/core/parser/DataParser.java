package io.github.serialdebug.core.parser;

public interface DataParser {
    byte[] encode(String text);
    String decode(byte[] data, int offset, int length);
}
