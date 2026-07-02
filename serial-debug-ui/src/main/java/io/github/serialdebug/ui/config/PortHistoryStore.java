package io.github.serialdebug.ui.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Persists port connection history to ~/.serialdebug/port-history.json.
 * Reuses Jackson pattern from JsonPresetService.
 */
public class PortHistoryStore {

    private static final int MAX_HISTORY = 20;
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public PortHistoryStore() {
        this(Paths.get(System.getProperty("home", "."), ".serialdebug", "port-history.json"));
    }

    public PortHistoryStore(Path file) {
        this.file = file;
    }

    public List<PortHistory> load() {
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            return mapper.readValue(file.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public synchronized void save(PortHistory entry) {
        List<PortHistory> all = load();
        // Remove existing entry for same port
        all.removeIf(h -> h.portName().equals(entry.portName()));
        all.add(entry);
        // Keep only most recent MAX_HISTORY
        all.sort(Comparator.comparingLong(PortHistory::lastConnected).reversed());
        if (all.size() > MAX_HISTORY) {
            all = all.subList(0, MAX_HISTORY);
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), "port-history", ".tmp");
            mapper.writeValue(tmp.toFile(), all);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Silently fail — history is best-effort
        }
    }

    public Optional<PortHistory> findByPort(String portName) {
        return load().stream()
                .filter(h -> h.portName().equals(portName))
                .max(Comparator.comparingLong(PortHistory::lastConnected));
    }

    public List<PortHistory> getRecent(int limit) {
        return load().stream()
                .sorted(Comparator.comparingLong(PortHistory::lastConnected).reversed())
                .limit(limit)
                .toList();
    }
}
