package io.github.serialdebug.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonProtocolStoreTest {

    private Path baseDir;
    private JsonProtocolStore store;

    @BeforeEach
    void setUp() throws IOException {
        baseDir = Files.createTempDirectory("protocol-store-test");
        store = new JsonProtocolStore(baseDir);
    }

    private static Protocol sample() {
        return new Protocol("test-sensor", "1.0",
                new ProtocolFraming("header", "AA55", 10),
                List.of(new ProtocolField("temp", "Temperature", 2, 2, "int16_le", 0.1, 0.0, null, true)));
    }

    @Test
    void shouldSaveAndLoadProtocol() throws IOException {
        store.save("my-sensor", sample());
        List<String> names = store.listNames();
        assertTrue(names.contains("my-sensor"));
        Optional<Protocol> loaded = store.load("my-sensor");
        assertTrue(loaded.isPresent());
        assertEquals("test-sensor", loaded.get().name());
        assertEquals("AA55", loaded.get().framing().header());
    }

    @Test
    void shouldListNames() throws IOException {
        store.save("a", sample());
        store.save("b", sample());
        List<String> names = store.listNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("a"));
        assertTrue(names.contains("b"));
    }

    @Test
    void shouldDeleteProtocol() throws IOException {
        store.save("x", sample());
        store.delete("x");
        assertFalse(store.load("x").isPresent());
    }

    @Test
    void shouldReturnEmptyForUnknownName() {
        assertFalse(store.load("nonexistent").isPresent());
    }

    @Test
    void shouldReturnEmptyListWhenDirectoryMissing() {
        JsonProtocolStore missing = new JsonProtocolStore(baseDir.resolve("does-not-exist"));
        assertTrue(missing.listNames().isEmpty());
    }

    @Test
    void shouldNotCrashOnCorruptJson() throws IOException {
        Files.write(baseDir.resolve("broken.json"), "NOT JSON {{{".getBytes());
        List<String> names = store.listNames();
        assertEquals(1, names.size());
        assertFalse(store.load("broken").isPresent());
    }
}
