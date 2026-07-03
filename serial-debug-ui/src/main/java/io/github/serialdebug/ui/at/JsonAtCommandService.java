package io.github.serialdebug.ui.at;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.serialdebug.ui.i18n.Messages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON-file-backed persistence for AT command templates.
 * Storage: ~/.serialdebug/at-commands.json
 * Atomic write (tmp + rename). Falls back to built-in defaults on read failure.
 */
public class JsonAtCommandService implements AtCommandService {

    private static final String APP_DIR = ".serialdebug";
    private static final String AT_COMMANDS_FILE = "at-commands.json";

    private final ObjectMapper mapper;
    private final Path filePath;

    public JsonAtCommandService() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.filePath = Path.of(System.getProperty("user.home"), APP_DIR, AT_COMMANDS_FILE);
    }

    /**
     * Load AT commands from disk. Returns built-in defaults if file missing or corrupt.
     */
    @Override
    public List<AtCommand> load() {
        if (!Files.exists(filePath)) {
            List<AtCommand> defaults = getDefaults();
            save(defaults);
            return defaults;
        }
        try {
            return mapper.readValue(filePath.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            return getDefaults();
        }
    }

    /**
     * Save AT commands to disk (atomic write).
     */
    @Override
    public void save(List<AtCommand> commands) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), commands);
            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Failed to save AT commands: " + e.getMessage());
        }
    }

    /**
     * Built-in common AT commands (10 entries).
     */
    public static List<AtCommand> getDefaults() {
        List<AtCommand> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(new AtCommand(
                    Messages.get("at.default." + i + ".name"),
                    Messages.get("at.default." + i + ".command"),
                    Messages.get("at.default." + i + ".desc")));
        }
        return list;
    }
}
