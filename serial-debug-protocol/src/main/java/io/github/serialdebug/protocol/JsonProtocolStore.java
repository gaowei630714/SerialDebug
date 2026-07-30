package io.github.serialdebug.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persists protocol definitions as JSON files under .serialdebug/protocols/. */
public class JsonProtocolStore implements ProtocolStore {

    private static final Path PROTOCOLS_DIR = Path.of(".serialdebug", "protocols");
    private final Path basePath;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public JsonProtocolStore() {
        this(Paths.get(System.getProperty("user.home")).resolve(PROTOCOLS_DIR));
    }

    public JsonProtocolStore(Path basePath) {
        this.basePath = basePath;
    }

    @Override
    public Optional<Protocol> load(String name) {
        Path file = basePath.resolve(sanitizeName(name) + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            Protocol protocol = mapper.readValue(file.toFile(), Protocol.class);
            return Optional.of(protocol);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized void save(String name, Protocol protocol) {
        try {
            Files.createDirectories(basePath);
            Path tmp = Files.createTempFile(basePath, sanitizeName(name) + "-", ".tmp");
            mapper.writeValue(tmp.toFile(), protocol);
            Files.move(tmp, basePath.resolve(sanitizeName(name) + ".json"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Persisting protocols is best-effort - don't crash the app
        }
    }

    @Override
    public List<String> listNames() {
        List<String> names = new ArrayList<>();
        if (!Files.exists(basePath)) {
            return names;
        }
        try {
            Files.newDirectoryStream(basePath, "*.json").forEach(path -> {
                String n = path.getFileName().toString();
                if (n.endsWith(".json")) {
                    names.add(n.substring(0, n.length() - 5));
                }
            });
        } catch (IOException e) {
            // Return whatever was accumulated
        }
        return names;
    }

    @Override
    public void delete(String name) {
        try {
            Files.deleteIfExists(basePath.resolve(sanitizeName(name) + ".json"));
        } catch (IOException e) {
            // best-effort
        }
    }

    private static String sanitizeName(String name) {
        // Keep only alphanumerics, dots, and hyphens. Hyphen at end is literal.
        return name.replaceAll("[^a-zA-Z0-9_.-]", "");
    }
}
