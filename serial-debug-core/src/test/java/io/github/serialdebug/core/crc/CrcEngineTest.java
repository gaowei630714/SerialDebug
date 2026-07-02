package io.github.serialdebug.core.crc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrcEngineTest {

    private static final byte[] SAMPLE = {0x01, 0x03, 0x00, 0x00, 0x00, 0x01};

    @Test
    void crc8Dallas() {
        assertEquals(0x22, CrcEngine.crc8Dallas(SAMPLE));
    }

    @Test
    void crc16Modbus() {
        assertEquals(0x0A84, CrcEngine.crc16Modbus(SAMPLE));
    }

    @Test
    void crc32() {
        assertEquals(0x4A393840L, CrcEngine.crc32(SAMPLE));
    }

    @Test
    void sum8() {
        int expected = 0x01 + 0x03 + 0x00 + 0x00 + 0x00 + 0x01;
        assertEquals(expected & 0xFF, CrcEngine.sum8(SAMPLE));
    }

    @Test
    void sum16() {
        int expected = 0x01 + 0x03 + 0x00 + 0x00 + 0x00 + 0x01;
        assertEquals(expected & 0xFFFF, CrcEngine.sum16(SAMPLE));
    }

    @Test
    void emptyInputReturnsInitial() {
        assertEquals(0x00, CrcEngine.crc8Dallas(new byte[0]));
        assertEquals(0x00, CrcEngine.sum8(new byte[0]));
    }

    @Test
    void singleByteInput() {
        byte[] data = {0x42};
        assertTrue(CrcEngine.crc8Dallas(data) >= 0);
        assertTrue(CrcEngine.crc16Modbus(data) >= 0);
    }
}
