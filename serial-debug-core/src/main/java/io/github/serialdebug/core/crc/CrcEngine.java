package io.github.serialdebug.core.crc;

/**
 * Pure Java CRC algorithm library. Zero dependencies.
 * Supports CRC-8/Dallas, CRC-16/Modbus, CRC-32, SUM-8, SUM-16.
 */
public final class CrcEngine {

    private CrcEngine() {}

    /** CRC-8 / Dallas 1-Wire: poly=0x31, init=0x00, xorout=0x00 */
    public static int crc8Dallas(byte[] data) {
        int crc = 0x00;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x80) != 0) crc = ((crc << 1) ^ 0x31) & 0xFF;
                else crc = (crc << 1) & 0xFF;
            }
        }
        return crc & 0xFF;
    }

    /** CRC-16 / Modbus: poly=0x8005, init=0xFFFF, xorout=0x0000 */
    public static int crc16Modbus(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) crc = ((crc >> 1) ^ 0xA001) & 0xFFFF;
                else crc = (crc >> 1) & 0xFFFF;
            }
        }
        return crc & 0xFFFF;
    }

    /** CRC-32: poly=0x04C11DB7, init=0xFFFFFFFF, xorout=0xFFFFFFFF */
    public static long crc32(byte[] data) {
        long crc = 0xFFFFFFFFL;
        for (byte b : data) {
            crc ^= (b & 0xFFL);
            for (int i = 0; i < 8; i++) {
                if ((crc & 1L) != 0) crc = (crc >> 1) ^ 0xEDB88320L;
                else crc >>= 1;
            }
        }
        return (crc ^ 0xFFFFFFFFL) & 0xFFFFFFFFL;
    }

    /** SUM-8: simple sum modulo 256 */
    public static int sum8(byte[] data) {
        int sum = 0;
        for (byte b : data) sum += (b & 0xFF);
        return sum & 0xFF;
    }

    /** SUM-16: simple sum modulo 65536 */
    public static int sum16(byte[] data) {
        int sum = 0;
        for (byte b : data) sum += (b & 0xFF);
        return sum & 0xFFFF;
    }
}
