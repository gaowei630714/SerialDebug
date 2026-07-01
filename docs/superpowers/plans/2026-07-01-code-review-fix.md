# Code Review Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 10 issues identified in code review across performance, thread safety, code quality, and maintainability.

**Architecture:** Three independent batches — (1) Core module performance + resource fixes, (2) UI thread safety with batch flush mechanism, (3) Quality cleanup (CSS, tests). Each batch is self-contained with independent test coverage.

**Tech Stack:** Java 17, JavaFX 21, jSerialComm, JUnit 5, Mockito, HexFormat (Java 17+)

---

## File Structure

### Files Modified

| File | Batch | Change |
|------|-------|--------|
| `serial-debug-core/src/main/java/io/github/serialdebug/core/parser/HexParser.java` | 1 | Replace `String.format` with `HexFormat` |
| `serial-debug-core/src/main/java/io/github/serialdebug/core/log/FileLogService.java` | 1 | Hex perf + bytesLogged split + error cleanup |
| `serial-debug-core/src/test/java/io/github/serialdebug/core/log/FileLogServiceTest.java` | 1 | Update split test, add error recovery test |
| `serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/MainController.java` | 2 | Batch flush + log offload + NPE defense |
| `serial-debug-ui/src/main/resources/style.css` | 3 | Add `.btn-danger` class |
| `serial-debug-core/src/test/java/io/github/serialdebug/core/serial/JSerialCommServiceTest.java` | 3 | New test file (optional) |

---

## Batch 1: Core Module Fixes

### Task 1: HexParser — replace String.format with HexFormat

**Files:**
- Modify: `serial-debug-core/src/main/java/io/github/serialdebug/core/parser/HexParser.java:24-37`

- [ ] **Step 1: Replace decode() impl**

Replace lines 24-37 (the `decode` method body) to use Java 17 `HexFormat`:

```java
@Override
public String decode(byte[] data, int offset, int length) {
    if (data == null || length == 0) {
        return "";
    }
    return HexFormat.of().withUpperCase().withDelimiter(" ")
            .formatHex(data, offset, length);
}
```

`HexFormat.of().withUpperCase().withDelimiter(" ").formatHex(data, offset, length)` produces space-separated uppercase hex like `"01 02 FF"`, matching the current output exactly. No test changes needed.

- [ ] **Step 2: Run existing tests to confirm**

```bash
cd C:/Users/gaowe/Desktop/ttl/serial-debug
$env:JAVA_HOME = "D:\soft\java\jdk17"
mvn test -pl serial-debug-core -Dtest=HexParserTest -q
```

Expected: all 8 tests PASS (existing test data already expects uppercase space-separated format).

- [ ] **Step 3: Commit**

```bash
git add serial-debug-core/src/main/java/io/github/serialdebug/core/parser/HexParser.java
git commit -m "perf(core): replace String.format with HexFormat in HexParser.decode"
```

---

### Task 2: FileLogService — bytesLogged split + hex encoding + error handling

**Files:**
- Modify: `serial-debug-core/src/main/java/io/github/serialdebug/core/log/FileLogService.java`

This task makes 3 changes to the same file.

- [ ] **Step 1: Replace encodeHex with HexFormat**

Replace `FileLogService.encodeHex()` (lines 163-171):

```java
private String encodeHex(byte[] data, int offset, int length) {
    return HexFormat.of().withUpperCase().withDelimiter(" ")
            .formatHex(data, offset, length);
}
```

Remove the unused import for `String` if it becomes unused (check existing imports — line 10 has `StandardCharsets` which stays, no `String.format` import needed since it's `java.lang.String`).

- [ ] **Step 2: Rename bytesLogged to currentFileBytes, add totalBytesLogged**

Change field declarations (line 27):

```java
private final ReentrantLock lock = new ReentrantLock();
// ... (other fields unchanged) ...
private long currentFileBytes;      // was: bytesLogged
private long totalBytesLogged;      // NEW
```

In `start()` (line 43), change the reset:

```java
this.currentFileBytes = 0;       // was: this.bytesLogged = 0;
this.totalBytesLogged = 0;       // NEW
```

In `log()` (line 98), change the accounting:

```java
// was: bytesLogged += line.length() + System.lineSeparator().length();
long lineBytes = line.length() + System.lineSeparator().length();
currentFileBytes += lineBytes;
totalBytesLogged += lineBytes;
```

In `getBytesLogged()` (lines 113-121):

```java
@Override
public long getBytesLogged() {
    lock.lock();
    try {
        return totalBytesLogged;    // was: return bytesLogged;
    } finally {
        lock.unlock();
    }
}
```

- [ ] **Step 3: Update checkAndSplit to use currentFileBytes**

Replace `checkAndSplit()` (lines 133-146):

```java
private void checkAndSplit() {
    if (currentFileBytes >= splitThreshold) {
        try {
            writer.flush();
            writer.close();
            splitIndex++;
            currentFile = generateSplitPath(baseFile, splitIndex);
            writer = Files.newBufferedWriter(currentFile, StandardCharsets.UTF_8);
            currentFileBytes = 0;
        } catch (IOException e) {
            // If split fails, continue writing to current file
        }
    }
}
```

Key changes:
- `currentFileBytes >= splitThreshold` replaces `Files.size(currentFile) >= splitThreshold`
- `currentFileBytes = 0` after opening new file

- [ ] **Step 4: Add currentFile = null in IOException handler**

Change the catch block in `log()` (lines 100-108):

```java
} catch (IOException e) {
    try {
        writer.close();
    } catch (IOException ignored) {
    }
    writer = null;
    currentFile = null;       // NEW: clear stale file reference
}
```

- [ ] **Step 5: Commit**

```bash
git add serial-debug-core/src/main/java/io/github/serialdebug/core/log/FileLogService.java
git commit -m "fix(core): bytesLogged split tracking, HexFormat, error cleanup"
```

---

### Task 3: FileLogServiceTest — update split test + add error test

**Files:**
- Modify: `serial-debug-core/src/test/java/io/github/serialdebug/core/log/FileLogServiceTest.java`

- [ ] **Step 1: Update shouldSplitFileWhenExceedsThreshold**

Replace the test method (lines 124-137):

```java
@Test
void shouldSplitFileWhenExceedsThreshold() throws Exception {
    FileLogService service = new FileLogService(30);
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
```

- [ ] **Step 2: Add shouldRecoverAfterWriteError test**

Add after `shouldSplitFileWhenExceedsThreshold`:

```java
@Test
void shouldNotLogAfterIOException() throws Exception {
    FileLogService service = new FileLogService();
    Path file = tempDir.resolve("io_error.log");
    service.start(file, LogFormat.HEX);
    assertTrue(service.isLogging());

    // Make file read-only to trigger IOException on write
    file.toFile().setReadOnly();

    service.log(new byte[]{0x01}, 0, 1, Direction.RX);
    assertFalse(service.isLogging(), "service should stop after write failure");
    assertNull(service.getCurrentFile(), "currentFile should be null after error");

    // Reset file permissions for cleanup
    file.toFile().setWritable(true);
    service.stop();
}
```

- [ ] **Step 3: Run all core tests**

```bash
cd C:/Users/gaowe/Desktop/ttl/serial-debug
$env:JAVA_HOME = "D:\soft\java\jdk17"
mvn test -pl serial-debug-core -q
```

Expected: all tests PASS (including existing `shouldSplitFileWhenExceedsThreshold`).

- [ ] **Step 4: Commit**

```bash
git add serial-debug-core/src/test/java/io/github/serialdebug/core/log/FileLogServiceTest.java
git commit -m "test(core): update split test, add IO error recovery test"
```

**Batch 1 checkpoint:** `mvn test -pl serial-debug-core` must pass before proceeding.

---

## Batch 2: UI Thread Safety

### Task 4: MainController — batch flush + log offload + NPE defense

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/MainController.java`

All changes in a single file. Make them in order.

- [ ] **Step 1: Add batch fields and BatchEntry record**

Add after the existing fields (after line 76, before `initialize`):

Add import for `java.util.ArrayList` and `java.util.concurrent.atomic.AtomicBoolean` (AtomicBoolean is already imported on line 36). Add ArrayList import:

```java
// line 36: import java.util.concurrent.atomic.AtomicBoolean;  // already there
// add:
import java.util.ArrayList;
```

Add inner record and fields after `presets` field:

```java
private record BatchEntry(String timestamp, String hex, String ascii, Direction dir) {}

private final List<BatchEntry> batchBuffer = new ArrayList<>();
private final AtomicBoolean batchPending = new AtomicBoolean(false);
```

- [ ] **Step 2: Refactor onDataReceived to use batch flush**

Replace `onDataReceived` (lines 429-445):

```java
private void onDataReceived(byte[] data) {
    String timestamp = LocalTime.now().format(TIME_FORMATTER);
    String hexStr = hexParser.decode(data, 0, data.length);
    String asciiStr = asciiParser.decode(data, 0, data.length);

    // Write log on listener thread (not UI thread)
    if (logService.isLogging()) {
        logService.log(data, 0, data.length, Direction.RX);
    }

    // Batch UI update: coalesce multiple rapid events into one Platform.runLater
    synchronized (batchBuffer) {
        batchBuffer.add(new BatchEntry(timestamp, hexStr, asciiStr, Direction.RX));
    }
    if (batchPending.compareAndSet(false, true)) {
        Platform.runLater(this::flushBatch);
    }
}
```

- [ ] **Step 3: Add flushBatch method**

Add after `onDataReceived` (before `updateStats`):

```java
private void flushBatch() {
    List<BatchEntry> entries;
    synchronized (batchBuffer) {
        entries = new ArrayList<>(batchBuffer);
        batchBuffer.clear();
    }
    for (BatchEntry e : entries) {
        if (hexViewArea.getLength() > 1_000_000) hexViewArea.clear();
        if (asciiViewArea.getLength() > 1_000_000) asciiViewArea.clear();
        hexViewArea.appendText("[" + e.timestamp + " " + e.dir + "] " + e.hex + "\n");
        asciiViewArea.appendText("[" + e.timestamp + " " + e.dir + "] " + e.ascii + "\n");
    }
    hexViewArea.setScrollTop(Double.MAX_VALUE);
    asciiViewArea.setScrollTop(Double.MAX_VALUE);
    updateStats();
    batchPending.set(false);
    // Re-arm if new data arrived during flush (race-safe)
    synchronized (batchBuffer) {
        if (!batchBuffer.isEmpty() && batchPending.compareAndSet(false, true)) {
            Platform.runLater(this::flushBatch);
        }
    }
}
```

- [ ] **Step 4: Refactor onSend to move log I/O out of Platform.runLater**

Replace the `onSend` method (lines 226-267):

```java
@FXML
private void onSend() {
    String text = sendTextField.getText();
    if (text == null || text.isEmpty()) return;
    byte[] data;
    if (hexModeToggle.isSelected()) {
        try {
            data = hexParser.encode(text);
        } catch (IllegalArgumentException e) {
            showError("Invalid HEX input", e);
            return;
        }
    } else {
        data = asciiParser.encode(text);
        if (appendNewlineCheckBox.isSelected()) {
            byte[] withNewline = new byte[data.length + 2];
            System.arraycopy(data, 0, withNewline, 0, data.length);
            withNewline[data.length] = '\r';
            withNewline[data.length + 1] = '\n';
            data = withNewline;
        }
    }
    try {
        serialService.sendData(data);
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String hexStr = hexParser.decode(data, 0, data.length);
        String asciiStr = asciiParser.decode(data, 0, data.length);

        // Write log before UI update (low-frequency TX, acceptable on JavaFX thread)
        if (logService.isLogging()) {
            logService.log(data, 0, data.length, Direction.TX);
        }

        final String ts = timestamp;
        final String hexDisplay = hexStr;
        final String asciiDisplay = asciiStr;
        Platform.runLater(() -> {
            hexViewArea.appendText("[" + ts + " TX] " + hexDisplay + "\n");
            asciiViewArea.appendText("[" + ts + " TX] " + asciiDisplay + "\n");
            hexViewArea.setScrollTop(Double.MAX_VALUE);
            asciiViewArea.setScrollTop(Double.MAX_VALUE);
        });
        updateStats();
    } catch (IOException e) {
        showError("Failed to send data", e);
    }
}
```

- [ ] **Step 5: Add NPE defense to openPort**

Replace `openPort` (lines 182-202):

```java
private void openPort() {
    SerialPortInfo selectedPort = portCombo.getValue();
    if (selectedPort == null) {
        showWarning("Please select a serial port");
        return;
    }
    Integer baudRate = baudRateCombo.getValue();
    if (baudRate == null) { showWarning("Please select baud rate"); return; }
    Integer dataBits = dataBitsCombo.getValue();
    if (dataBits == null) { showWarning("Please select data bits"); return; }
    Integer stopBits = stopBitsCombo.getValue();
    if (stopBits == null) { showWarning("Please select stop bits"); return; }
    SerialConfig.Parity parity = parityCombo.getValue();
    if (parity == null) { showWarning("Please select parity"); return; }

    SerialConfig config = new SerialConfig();
    config.setPortName(selectedPort.getSystemPortName());
    config.setBaudRate(baudRate);
    config.setDataBits(dataBits);
    config.setStopBits(stopBits);
    config.setParity(parity);
    try {
        serialService.open(config);
        isOpen.set(true);
        updatePortState(true);
        appendStatus("Connected: " + config);
    } catch (IOException e) {
        showError("Failed to open port", e);
    }
}
```

- [ ] **Step 6: Verify compilation**

```bash
cd C:/Users/gaowe/Desktop/ttl/serial-debug
$env:JAVA_HOME = "D:\soft\java\jdk17"
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/MainController.java
git commit -m "fix(ui): batch flush for data received, offload log I/O, NPE defense"
```

**Batch 2 checkpoint:** `mvn compile` must pass.

---

## Batch 3: Quality Cleanup

### Task 5: CSS class for button styles

**Files:**
- Modify: `serial-debug-ui/src/main/resources/style.css`
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/MainController.java`

- [ ] **Step 1: Add .btn-danger CSS class**

Append to `style.css`:

```css
.btn-danger {
    -fx-background-color: #e74c3c;
    -fx-text-fill: white;
}
```

- [ ] **Step 2: Update updatePortState to use styleClass**

In `MainController.java`, replace `updatePortState` (lines 214-224):

```java
private void updatePortState(boolean connected) {
    if (connected) {
        openCloseButton.setText("Close");
        openCloseButton.getStyleClass().add("btn-danger");
        connectionStatusLabel.setText("Connected: " + serialService.getCurrentConfig());
    } else {
        openCloseButton.setText("Open");
        openCloseButton.getStyleClass().remove("btn-danger");
        connectionStatusLabel.setText("Disconnected");
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd C:/Users/gaowe/Desktop/ttl/serial-debug
$env:JAVA_HOME = "D:\soft\java\jdk17"
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add serial-debug-ui/src/main/resources/style.css serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/MainController.java
git commit -m "style(ui): replace inline button styles with CSS class"
```

---

### Task 6: JSerialCommServiceTest (optional)

**Files:**
- Create: `serial-debug-core/src/test/java/io/github/serialdebug/core/serial/JSerialCommServiceTest.java`

This test uses Mockito inline mock for mocking `SerialPort` static methods. Since Mockito 3.4+ supports static mocking via `mockStatic()`, no extra configuration is needed.

- [ ] **Step 1: Create test file**

Create `serial-debug-core/src/test/java/io/github/serialdebug/core/serial/JSerialCommServiceTest.java`:

```java
package io.github.serialdebug.core.serial;

import com.fazecast.jSerialComm.SerialPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JSerialCommServiceTest {

    @Test
    void shouldListPorts() {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.getSystemPortName()).thenReturn("COM3");
        when(mockPort.getDescriptivePortName()).thenReturn("USB Serial Port");

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(SerialPort::getCommPorts).thenReturn(new SerialPort[]{mockPort});

            JSerialCommService service = new JSerialCommService();
            var ports = service.listPorts();

            assertEquals(1, ports.size());
            assertEquals("COM3", ports.get(0).getSystemPortName());
            assertEquals("USB Serial Port", ports.get(0).getDescription());
        }
    }

    @Test
    void shouldOpenPortSuccessfully() throws Exception {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.openPort()).thenReturn(true);

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(() -> SerialPort.getCommPort(anyString())).thenReturn(mockPort);

            JSerialCommService service = new JSerialCommService();
            SerialConfig config = new SerialConfig();
            config.setPortName("COM3");

            service.open(config);
            assertTrue(service.isOpen());
        }
    }

    @Test
    void shouldThrowOnOpenFailure() {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.openPort()).thenReturn(false);

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(() -> SerialPort.getCommPort(anyString())).thenReturn(mockPort);

            JSerialCommService service = new JSerialCommService();
            SerialConfig config = new SerialConfig();
            config.setPortName("COM3");

            assertThrows(IOException.class, () -> service.open(config));
            assertFalse(service.isOpen());
        }
    }

    @Test
    void shouldThrowOnOpenWhenAlreadyOpen() throws Exception {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.openPort()).thenReturn(true);
        when(mockPort.isOpen()).thenReturn(true);

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(() -> SerialPort.getCommPort(anyString())).thenReturn(mockPort);

            JSerialCommService service = new JSerialCommService();
            SerialConfig config = new SerialConfig();
            config.setPortName("COM3");
            service.open(config);

            assertThrows(IOException.class, () -> service.open(config));
        }
    }

    @Test
    void shouldSendData() throws Exception {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.openPort()).thenReturn(true);
        when(mockPort.isOpen()).thenReturn(true);
        when(mockPort.writeBytes(any(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(() -> SerialPort.getCommPort(anyString())).thenReturn(mockPort);

            JSerialCommService service = new JSerialCommService();
            SerialConfig config = new SerialConfig();
            config.setPortName("COM3");
            service.open(config);

            byte[] data = "hello".getBytes();
            service.sendData(data);

            verify(mockPort).writeBytes(data, data.length);
        }
    }

    @Test
    void shouldThrowOnSendWhenNotOpen() {
        JSerialCommService service = new JSerialCommService();
        assertThrows(IOException.class, () -> service.sendData(new byte[]{0x01}));
    }

    @Test
    void shouldClosePort() throws Exception {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.openPort()).thenReturn(true);
        when(mockPort.isOpen()).thenReturn(true);

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(() -> SerialPort.getCommPort(anyString())).thenReturn(mockPort);

            JSerialCommService service = new JSerialCommService();
            SerialConfig config = new SerialConfig();
            config.setPortName("COM3");
            service.open(config);
            assertTrue(service.isOpen());

            service.close();
            assertFalse(service.isOpen());
            verify(mockPort).removeDataListener();
            verify(mockPort).closePort();
        }
    }
}
```

- [ ] **Step 2: Run the test**

```bash
cd C:/Users/gaowe/Desktop/ttl/serial-debug
$env:JAVA_HOME = "D:\soft\java\jdk17"
mvn test -pl serial-debug-core -Dtest=JSerialCommServiceTest -q
```

Expected: all tests PASS. If Mockito `mockStatic` is not available, verify the pom.xml has Mockito 3.4+ (check `mockito-core` or `mockito-inline` version in pom.xml).

- [ ] **Step 3: Commit**

```bash
git add serial-debug-core/src/test/java/io/github/serialdebug/core/serial/JSerialCommServiceTest.java
git commit -m "test(core): add JSerialCommService unit tests with mocked SerialPort"
```

---

## Final Validation

- [ ] Run all tests:

```bash
cd C:/Users/gaowe/Desktop/ttl/serial-debug
$env:JAVA_HOME = "D:\soft\java\jdk17"
mvn clean test -q
```

Expected: all tests PASS.

- [ ] Verify compilation of entire project:

```bash
mvn clean compile -q
```

Expected: BUILD SUCCESS.
