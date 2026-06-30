# Log-to-File Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add serial data logging to text files with HEX/ASCII format support, automatic file splitting, and live status display.

**Architecture:** New `LogService` interface + `FileLogService` implementation in `serial-debug-core/log/` package (zero UI dependency). `MainController` injects `LogService`, calls `log()` from `onDataReceived()` and `onSend()`. UI adds start/stop buttons, format toggle, and status label.

**Tech Stack:** Java 17, Maven, JUnit 5, JavaFX (UI layer only)

---

## File Structure

```
serial-debug/
├── serial-debug-core/
│   └── src/
│       ├── main/java/io/github/serialdebug/core/log/
│       │   ├── LogService.java              # interface
│       │   ├── LogFormat.java               # enum: HEX, ASCII
│       │   ├── Direction.java               # enum: RX, TX
│       │   └── FileLogService.java          # file I/O impl
│       └── test/java/io/github/serialdebug/core/log/
│           └── FileLogServiceTest.java      # unit tests
└── serial-debug-ui/
    └── src/main/
        ├── java/io/github/serialdebug/ui/controller/
        │   └── MainController.java           # modified: inject LogService, wire buttons
        └── resources/io/github/serialdebug/ui/
            └── main-view.fxml                # modified: add logging controls
```

---

### Task 1: LogFormat enum

**Files:**
- Create: `serial-debug-core/src/main/java/io/github/serialdebug/core/log/LogFormat.java`

- [ ] **Step 1: Create the enum**

```java
package io.github.serialdebug.core.log;

public enum LogFormat {
    HEX,
    ASCII
}
```

- [ ] **Step 2: Compile to verify**

Run: `mvn compile -pl serial-debug-core -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add serial-debug-core/src/main/java/io/github/serialdebug/core/log/LogFormat.java
git commit -m "feat(log): add LogFormat enum (HEX, ASCII)"
```

---

### Task 2: Direction enum

**Files:**
- Create: `serial-debug-core/src/main/java/io/github/serialdebug/core/log/Direction.java`

- [ ] **Step 1: Create the enum**

```java
package io.github.serialdebug.core.log;

public enum Direction {
    RX,
    TX
}
```

- [ ] **Step 2: Compile to verify**

Run: `mvn compile -pl serial-debug-core -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add serial-debug-core/src/main/java/io/github/serialdebug/core/log/Direction.java
git commit -m "feat(log): add Direction enum (RX, TX)"
```

---

### Task 3: LogService interface

**Files:**
- Create: `serial-debug-core/src/main/java/io/github/serialdebug/core/log/LogService.java`

- [ ] **Step 1: Create the interface**

```java
package io.github.serialdebug.core.log;

import java.io.IOException;
import java.nio.file.Path;

public interface LogService {

    /**
     * Start logging to the specified file.
     *
     * @param file   target file path
     * @param format log format (HEX or ASCII)
     * @throws IOException if the file cannot be created or opened
     */
    void start(Path file, LogFormat format) throws IOException;

    /** Stop logging and close the file stream. */
    void stop();

    /** Whether logging is currently active. */
    boolean isLogging();

    /**
     * Write one log entry.
     *
     * @param data      raw byte data
     * @param offset    start offset in data
     * @param length    number of valid bytes
     * @param direction RX or TX
     */
    void log(byte[] data, int offset, int length, Direction direction);

    /** Total bytes written to file (flushed). */
    long getBytesLogged();

    /** Path of the currently active log file. */
    Path getCurrentFile();
}
```

- [ ] **Step 2: Compile to verify**

Run: `mvn compile -pl serial-debug-core -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add serial-debug-core/src/main/java/io/github/serialdebug/core/log/LogService.java
git commit -m "feat(log): add LogService interface"
```

---

### Task 4: FileLogService — failing tests (TDD red)

**Files:**
- Create: `serial-debug-core/src/test/java/io/github/serialdebug/core/log/FileLogServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
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

        String content = Files.readString(file);
        // format: [HH:mm:ss.SSS RX]
        assertTrue(content.matches("\\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3} RX\\].*"));
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

        // Write enough data to exceed threshold (each line is ~30+ bytes)
        for (int i = 0; i < 10; i++) {
            service.log("AAAAAAAAAAAAAAAAAA".getBytes(), 0, 18, Direction.RX);
        }
        service.stop();

        // A split file should exist
        assertTrue(Files.exists(tempDir.resolve("split_1.log")));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl serial-debug-core -am -Dtest=FileLogServiceTest`
Expected: BUILD FAILURE — `FileLogService` class not found

- [ ] **Step 3: Commit the tests**

```bash
git add serial-debug-core/src/test/java/io/github/serialdebug/core/log/FileLogServiceTest.java
git commit -m "test(log): add FileLogService unit tests (TDD red)"
```

---

### Task 5: FileLogService — implementation (TDD green)

**Files:**
- Create: `serial-debug-core/src/main/java/io/github/serialdebug/core/log/FileLogService.java`

- [ ] **Step 1: Write the implementation**

```java
package io.github.serialdebug.core.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

public class FileLogService implements LogService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final long DEFAULT_SPLIT_THRESHOLD = 100L * 1024 * 1024; // 100 MB

    private final long splitThreshold;
    private final ReentrantLock lock = new ReentrantLock();

    private BufferedWriter writer;
    private LogFormat format;
    private Path currentFile;
    private Path baseFile;
    private int splitIndex;
    private long bytesLogged;

    public FileLogService() {
        this(DEFAULT_SPLIT_THRESHOLD);
    }

    public FileLogService(long splitThreshold) {
        this.splitThreshold = splitThreshold;
    }

    @Override
    public void start(Path file, LogFormat format) throws IOException {
        lock.lock();
        try {
            this.baseFile = file;
            this.splitIndex = 0;
            this.format = format;
            this.bytesLogged = 0;
            this.currentFile = file;
            this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException ignored) {
                    // best effort on close
                }
                writer = null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isLogging() {
        lock.lock();
        try {
            return writer != null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void log(byte[] data, int offset, int length, Direction direction) {
        if (data == null || length <= 0) {
            return;
        }
        lock.lock();
        try {
            if (writer == null) {
                return;
            }
            String timestamp = LocalTime.now().format(TIME_FORMATTER);
            String payload = switch (format) {
                case HEX -> encodeHex(data, offset, length);
                case ASCII -> encodeAscii(data, offset, length);
            };
            String line = "[" + timestamp + " " + direction + "] " + payload;
            writer.write(line);
            writer.newLine();
            writer.flush();
            bytesLogged += line.length() + System.lineSeparator().length();
            checkAndSplit();
        } catch (IOException e) {
            // On write failure, stop logging to prevent further errors
            try {
                writer.close();
            } catch (IOException ignored) {
                // best effort
            }
            writer = null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long getBytesLogged() {
        lock.lock();
        try {
            return bytesLogged;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Path getCurrentFile() {
        lock.lock();
        try {
            return currentFile;
        } finally {
            lock.unlock();
        }
    }

    private void checkAndSplit() {
        try {
            if (currentFile != null && Files.exists(currentFile)
                    && Files.size(currentFile) >= splitThreshold) {
                writer.flush();
                writer.close();
                splitIndex++;
                currentFile = generateSplitPath(baseFile, splitIndex);
                writer = Files.newBufferedWriter(currentFile, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // If split fails, continue writing to current file
        }
    }

    private Path generateSplitPath(Path base, int index) {
        String fileName = base.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName;
        String extension;
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            baseName = fileName;
            extension = "";
        }
        return base.resolveSibling(baseName + "_" + index + extension);
    }

    private String encodeHex(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(length * 3);
        for (int i = offset; i < offset + length; i++) {
            if (i > offset) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", data[i]));
        }
        return sb.toString();
    }

    private String encodeAscii(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = offset; i < offset + length; i++) {
            byte b = data[offset + (i - offset)];
            int ub = b & 0xFF;
            if (ub >= 0x20 && ub <= 0x7E) {
                sb.append((char) ub);
            } else {
                sb.append(String.format("\\x%02X", ub));
            }
        }
        return sb.toString();
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `mvn test -pl serial-debug-core -am -Dtest=FileLogServiceTest`
Expected: BUILD SUCCESS, all 10 tests pass

- [ ] **Step 3: Run full test suite to check for regressions**

Run: `mvn test`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add serial-debug-core/src/main/java/io/github/serialdebug/core/log/FileLogService.java
git commit -m "feat(log): implement FileLogService with file splitting"
```

---

### Task 6: Update main-view.fxml — add logging controls

**Files:**
- Modify: `serial-debug-ui/src/main/resources/io/github/serialdebug/ui/main-view.fxml`

- [ ] **Step 1: Add logging controls to the toolbar**

Inside the `<ToolBar>` element, after the existing clear button and its separator, add:

```xml
        <Separator orientation="VERTICAL" />
        <Button fx:id="startLoggingButton" onAction="#onStartLogging">
            <graphic><FontIcon iconLiteral="mdi2c-content-save" /></graphic>
        </Button>
        <Button fx:id="stopLoggingButton" onAction="#onStopLogging" disable="true">
            <graphic><FontIcon iconLiteral="mdi2s-stop" /></graphic>
        </Button>
```

- [ ] **Step 2: Add logging format toggle to the toolbar**

After the stop button, add a label and toggle button group for format selection:

```xml
        <Label text="Log:" />
        <ToggleButton fx:id="logHexToggle" text="HEX" selected="true" />
        <ToggleButton fx:id="logAsciiToggle" text="ASCII" />
```

Wire the two toggles so they are mutually exclusive (selecting one deselects the other).

- [ ] **Step 4: Add logging status to the status bar**

Inside the `<HBox styleClass="status-bar">` element, add a new Label before the RX/TX labels:

```xml
        <Label fx:id="loggingStatusLabel" text="Not recording" />
```

- [ ] **Step 5: Compile to verify FXML is valid**

Run: `mvn compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add serial-debug-ui/src/main/resources/io/github/serialdebug/ui/main-view.fxml
git commit -m "feat(ui): add logging controls to toolbar and status bar"
```

---

### Task 7: Update MainController — wire logging

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/MainController.java`

- [ ] **Step 1: Add imports**

Add these imports at the top of the file (after existing imports):

```java
import io.github.serialdebug.core.log.Direction;
import io.github.serialdebug.core.log.FileLogService;
import io.github.serialdebug.core.log.LogFormat;
import io.github.serialdebug.core.log.LogService;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
```

- [ ] **Step 2: Add new fields**

After the existing field declarations (after `private final AtomicBoolean isOpen`), add:

```java
private final LogService logService = new FileLogService();

@FXML private Button startLoggingButton;
@FXML private Button stopLoggingButton;
@FXML private ToggleButton logHexToggle;
@FXML private ToggleButton logAsciiToggle;
@FXML private Label loggingStatusLabel;
```

- [ ] **Step 3: Wire format toggles in initialize()**

At the end of the `initialize()` method, add mutual-exclusion logic for the two format toggles:

```java
logHexToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
    if (newVal) logAsciiToggle.setSelected(false);
});
logAsciiToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
    if (newVal) logHexToggle.setSelected(false);
});
```

- [ ] **Step 4: Wire logging into onDataReceived()**

In the `onDataReceived()` method, inside the `Platform.runLater()` block, after the `updateStats()` call, add:

```java
if (logService.isLogging()) {
    logService.log(data, 0, data.length, Direction.RX);
}
```

- [ ] **Step 5: Wire logging into onSend()**

In the `onSend()` method, inside the `Platform.runLater()` block, after the TX text is appended to the text areas, add:

```java
if (logService.isLogging()) {
    logService.log(data, 0, data.length, Direction.TX);
}
```

- [ ] **Step 6: Add logging event handlers**

Add these methods after the existing `onClear()` method:

```java
@FXML
private void onStartLogging() {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Save Log File");
    fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Log Files", "*.log", "*.txt"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
    );
    fileChooser.setInitialFileName("serial_" +
            java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
            ".log");
    Stage stage = (Stage) startLoggingButton.getScene().getWindow();
    java.io.File file = fileChooser.showSaveDialog(stage);
    if (file == null) {
        return;
    }
    try {
        LogFormat chosenFormat = logAsciiToggle.isSelected() ? LogFormat.ASCII : LogFormat.HEX;
        logService.start(file.toPath(), chosenFormat);
        startLoggingButton.setDisable(true);
        stopLoggingButton.setDisable(false);
        logHexToggle.setDisable(true);
        logAsciiToggle.setDisable(true);
        updateLoggingStatus();
    } catch (IOException e) {
        showError("Failed to start logging", e);
    }
}

@FXML
private void onStopLogging() {
    logService.stop();
    startLoggingButton.setDisable(false);
    stopLoggingButton.setDisable(true);
    logHexToggle.setDisable(false);
    logAsciiToggle.setDisable(false);
    updateLoggingStatus();
}
```

- [ ] **Step 7: Add status update helper**

Add this method after `updateStats()`:

```java
private void updateLoggingStatus() {
    if (logService.isLogging()) {
        loggingStatusLabel.setText("Recording: " + logService.getCurrentFile().getFileName()
                + " (" + (logService.getBytesLogged() / 1024) + " KB)");
    } else {
        loggingStatusLabel.setText("Not recording");
    }
}
```

- [ ] **Step 8: Call updateLoggingStatus from updateStats()**

In the `updateStats()` method, at the end of the `Platform.runLater()` block, add:

```java
updateLoggingStatus();
```

- [ ] **Step 9: Compile to verify**

Run: `mvn compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 10: Run full test suite**

Run: `mvn test`
Expected: BUILD SUCCESS

- [ ] **Step 11: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/MainController.java
git commit -m "feat(ui): wire LogService into MainController"
```

---

### Task 8: Final verification

- [ ] **Step 1: Full build**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Full test suite**

Run: `mvn test`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 3: Verify the application launches**

Run: `mvn javafx:run -pl serial-debug-app`
Expected: Application window opens, toolbar shows logging buttons, status bar shows "Not recording"

- [ ] **Step 4: Final commit (if any uncommitted changes)**

```bash
git status
```
Expected: working tree clean
