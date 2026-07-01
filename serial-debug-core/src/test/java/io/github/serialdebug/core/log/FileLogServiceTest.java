package io.github.serialdebug.core.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileLogServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldNotLogWhenStopped() {
        FileLogService service = new FileLogService();
        assertFalse(service.isLogging());
        service.log("hello".getBytes(), 0, 5, Direction.RX);
        assertEquals(0, service.getBytesLogged());
    }

    @Test
    void shouldCreateFileOnStart() throws Exception {
        FileLogService service = new FileLogService();
        Path file = tempDir.resolve("test.log");
        service.start(file, LogFormat.HEX);
        assertTrue(service.isLogging());
        assertTrue(Files.exists(file));
        service.stop();
    }

    @Test
    void shouldWriteHexString() throws Exception {
        FileLogService service = new FileLogService();
        Path file = tempDir.resolve("hex.log");
        service.start(file, LogFormat.HEX);
        byte[] data = {0x01, 0x02, (byte) 0xFF};
        service.log(data, 0, 3, Direction.RX);
        service.stop();

        String content = Files.readString(file);
        assertTrue(content.contains("RX"));
        assertTrue(content.contains("01 02 FF"));
    }

    @Test
    void shouldWriteAsciiString() throws Exception {
        FileLogService service = new FileLogService();
        Path file = tempDir.resolve("ascii.log");
        service.start(file, LogFormat.ASCII);
        byte[] data = {'H', 'e', 'l', 'l', 'o'};
        service.log(data, 0, 5, Direction.RX);
        service.stop();

        String content = Files.readString(file);
        assertTrue(content.contains("RX"));
        assertTrue(content.contains("Hello"));
    }

    @Test
    void shouldEscapeNonPrintableInAsciiMode() throws Exception {
        FileLogService service = new FileLogService();
        Path file = tempDir.resolve("escape.log");
        service.start(file, LogFormat.ASCII);
        byte[] data = {'A', 0x00, 'B', '\r', '\n'};
        service.log(data, 0, 5, Direction.TX);
        service.stop();

        String content = Files.readString(file);
        assertTrue(content.contains("TX"));
        assertTrue(content.contains("\\x00"));
        assertTrue(content.contains("\\x0D"));
        assertTrue(content.contains("\\x0A"));
    }

    @Test
    void shouldIncludeTimestamp() throws Exception {
        FileLogService service = new FileLogService();
        Path file = tempDir.resolve("ts.log");
        service.start(file, LogFormat.HEX);
        service.log(new byte[]{0x42}, 0, 1, Direction.RX);
        service.stop();

        String content = Files.readString(file).trim();
        // format: [HH:mm:ss.SSS RX] followed by payload
        assertTrue(content.matches("\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3} RX\\] .*"));
    }

    @Test
    void shouldCloseFileOnStop() throws Exception {
        FileLogService service = new FileLogService();
        Path file = tempDir.resolve("close.log");
        service.start(file, LogFormat.HEX);
        service.log(new byte[]{0x01}, 0, 1, Direction.RX);
        service.stop();
        assertFalse(service.isLogging());

        // File should be readable after close
        String content = Files.readString(file);
        assertTrue(content.contains("01"));
    }

    @Test
    void shouldThrowOnInvalidPath() {
        FileLogService service = new FileLogService();
        Path invalid = tempDir.resolve("nonexistent_dir").resolve("file.log");
        assertThrows(java.io.IOException.class,
                () -> service.start(invalid, LogFormat.HEX));
    }

    @Test
    void shouldIncrementBytesLogged() throws Exception {
        FileLogService service = new FileLogService();
        Path file = tempDir.resolve("bytes.log");
        service.start(file, LogFormat.HEX);
        service.log(new byte[]{0x01, 0x02, 0x03}, 0, 3, Direction.RX);
        service.stop();
        assertTrue(service.getBytesLogged() > 0);
    }

    @Test
    void shouldSplitFileWhenExceedsThreshold() throws Exception {
        FileLogService service = new FileLogService(30); // 30-byte threshold for test
        Path file = tempDir.resolve("split.log");
        service.start(file, LogFormat.ASCII);

        for (int i = 0; i < 10; i++) {
            service.log("AAAAAAAAAAAAAAAAAA".getBytes(), 0, 18, Direction.RX);
        }

        long totalLogged = service.getBytesLogged();
        assertTrue(totalLogged > 30, "bytesLogged should exceed split threshold");
        assertTrue(Files.exists(tempDir.resolve("split_1.log")),
                "split_1.log should exist after split");

        service.stop();
    }

}
