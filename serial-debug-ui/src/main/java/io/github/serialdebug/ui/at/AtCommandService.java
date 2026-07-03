package io.github.serialdebug.ui.at;

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
 * JSON-file-backed persistence for AT command templates.
 * Storage: ~/.serialdebug/at-commands.json
 * Atomic write (tmp + rename). Falls back to built-in defaults on read failure.
 */
public class AtCommandService {

    private static final String APP_DIR = ".serialdebug";
    private static final String AT_COMMANDS_FILE = "at-commands.json";

    private final ObjectMapper mapper;
    private final Path filePath;

    public AtCommandService() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.filePath = Path.of(System.getProperty("user.home"), APP_DIR, AT_COMMANDS_FILE);
    }

    /**
     * Load AT commands from disk. Returns built-in defaults if file missing or corrupt.
     */
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
        list.add(new AtCommand("测试通信", "AT", "Basic communication test"));
        list.add(new AtCommand("查询厂商", "AT+CGMI", "Request manufacturer identification"));
        list.add(new AtCommand("查询型号", "AT+CGMM", "Request model identification"));
        list.add(new AtCommand("查询 IMEI", "AT+CGSN", "Request IMEI number"));
        list.add(new AtCommand("查询信号强度", "AT+CSQ", "Query signal quality"));
        list.add(new AtCommand("查询网络注册", "AT+CREG?", "Query network registration"));
        list.add(new AtCommand("查询 APN", "AT+CGDCONT?", "Query APN configuration"));
        list.add(new AtCommand("查询电池", "AT+CBC", "Query battery status"));
        list.add(new AtCommand("查询时间", "AT+CCLK?", "Query real-time clock"));
        list.add(new AtCommand("重启模块", "AT+CFUN=1,1", "Module reset"));
        return list;
    }
}
