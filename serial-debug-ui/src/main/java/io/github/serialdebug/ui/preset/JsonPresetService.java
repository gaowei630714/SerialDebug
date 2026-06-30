package io.github.serialdebug.ui.preset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON-file-backed implementation of {@link PresetService}.
 * <p>
 * Presets are stored at {@code ~/.serialdebug/presets.json}. Writes are atomic:
 * data is flushed to a temporary file first, then renamed over the target.
 */
public class JsonPresetService implements PresetService {

    private static final String APP_DIR = ".serialdebug";
    private static final String PRESETS_FILE = "presets.json";

    private final ObjectMapper mapper;
    private final Path presetsPath;

    public JsonPresetService() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.presetsPath = Path.of(System.getProperty("user.home"), APP_DIR, PRESETS_FILE);
    }

    @Override
    public List<Preset> load() {
        if (!Files.exists(presetsPath)) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(presetsPath.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            // Corrupt or unreadable file — start fresh rather than crash.
            return new ArrayList<>();
        }
    }

    @Override
    public void save(List<Preset> presets) {
        try {
            Path parent = presetsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = presetsPath.resolveSibling(presetsPath.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), presets);
            Files.move(tmp, presetsPath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Best-effort: log to stderr if persistence fails.
            System.err.println("Failed to save presets: " + e.getMessage());
        }
    }
}
