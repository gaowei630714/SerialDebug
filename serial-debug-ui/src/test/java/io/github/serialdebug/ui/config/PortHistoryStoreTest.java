package io.github.serialdebug.ui.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PortHistoryStoreTest {

    @TempDir
    Path tempDir;

    private PortHistoryStore store() {
        return new PortHistoryStore(tempDir.resolve("test-history.json"));
    }

    @Test
    void shouldSaveAndFind() {
        PortHistoryStore s = store();
        s.save(new PortHistory("COM3", 115200, 8, 1, "NONE", "NONE", 1000L));

        Optional<PortHistory> found = s.findByPort("COM3");
        assertTrue(found.isPresent());
        assertEquals(115200, found.get().baudRate());
    }

    @Test
    void shouldUpdateExistingPort() {
        PortHistoryStore s = store();
        s.save(new PortHistory("COM3", 9600, 8, 1, "NONE", "NONE", 1000L));
        s.save(new PortHistory("COM3", 115200, 8, 1, "NONE", "NONE", 2000L));
        List<PortHistory> all = s.load();
        assertEquals(1, all.size());
        assertEquals(115200, all.get(0).baudRate());
    }

    @Test
    void shouldReturnEmptyForMissing() {
        PortHistoryStore s = store();
        assertFalse(s.findByPort("NONEXISTENT").isPresent());
    }

    @Test
    void shouldLimitHistory() {
        PortHistoryStore s = store();
        for (int i = 0; i < 25; i++) {
            s.save(new PortHistory("COM" + i, 115200, 8, 1, "NONE", "NONE", i));
        }
        List<PortHistory> all = s.load();
        assertEquals(20, all.size());
    }
}
