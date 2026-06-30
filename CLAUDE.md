# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SerialDebug is a cross-platform serial port debugging tool for embedded development, IoT debugging, and sensor data acquisition. Built with Java 17 + JavaFX 21 + jSerialComm, targeting offline-friendly deployment via jlink-packaged self-contained JRE.

**License:** Apache 2.0

## Build & Development Commands

```bash
# Compile all modules
mvn clean compile

# Run all tests
mvn test

# Run tests for a single module
mvn test -pl serial-debug-core

# Run a single test class
mvn test -pl serial-debug-core -Dtest=HexParserTest

# Run a single test method
mvn test -pl serial-debug-core -Dtest=HexParserTest#shouldEncodeSimpleHex

# Run the application (development mode)
mvn javafx:run -pl serial-debug-app

# Package (creates runnable JAR)
mvn clean package -DskipTests
```

## Environment Requirements

- **JDK 17+** — The project targets Java 17 (compiler source/target). The system default JDK is 11, which will cause compilation failure. You must set `JAVA_HOME` to a JDK 17+ installation before building.
- **Maven 3.8+**

Windows PowerShell setup:
```powershell
$env:JAVA_HOME = "D:\soft\java\jdk17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## Architecture

### Module Dependency Graph

```
serial-debug-app → serial-debug-ui → serial-debug-core
                                    → serial-debug-protocol (SPI, optional)
```

### Module Responsibilities

| Module | Role | Key Dependencies |
|---|---|---|
| `serial-debug-core` | Zero-UI core: serial port operations, data parsing, config model | jSerialComm, Jackson |
| `serial-debug-ui` | JavaFX GUI: FXML views, controllers, styling | JavaFX 21, ControlsFX, Ikonli |
| `serial-debug-protocol` | SPI interface definitions for custom protocol extensions (placeholder) | — |
| `serial-debug-app` | Entry point + jlink packaging configuration | Aggregates core + ui |

### Core Design Patterns

- **Strategy pattern** — `DataParser` interface with `HexParser` and `AsciiParser` implementations for encode/decode. New parsers (e.g., Modbus, custom binary protocols) implement `DataParser` and can be wired in via the protocol module's SPI.
- **Interface segregation** — `SerialService` defines the serial port contract; `JSerialCommService` is the jSerialComm-backed implementation. The UI layer depends only on the interface, not the implementation.
- **Observer pattern** — Data reception uses `Consumer<byte[]>` callbacks. The callback fires on jSerialComm's listener thread; UI code must wrap updates in `Platform.runLater()`.

### Threading Model

- jSerialComm's `SerialPortDataListener` fires on a background thread.
- `MainController.onDataReceived()` marshals to JavaFX Application Thread via `Platform.runLater()`.
- Byte counters (`bytesReceived`, `bytesSent`) are `volatile long` — atomic reads, no locking.

### Data Flow

```
SerialPort (jSerialComm) → SerialPortDataListener → Consumer<byte[]> callback
    → timestamp + hex/ascii decode → Platform.runLater → TextArea append
```

Received data over 1MB in a TextArea triggers automatic clear to prevent memory overflow (v0.1 safeguard; ring buffer planned for future versions).

### Key Classes

- `SerialConfig` — Mutable value object with validation (baud rate > 0, data bits 5-8, stop bits 1-2). Uses `Parity` and `FlowControl` enums.
- `SerialPortInfo` — Immutable value object, equality based on `systemPortName`.
- `HexParser` — Uses `java.util.HexFormat` (Java 17+). Accepts space-separated or continuous hex strings. Decode produces uppercase space-separated format.
- `AsciiParser` — Uses `US_ASCII` charset. Non-printable bytes (outside 0x20-0x7E) rendered as `.` on decode.
- `MainController` — Single controller for the entire UI. FXML-injected fields, `@FXML` event handlers.

### FXML / UI Structure

- Single view: `main-view.fxml` bound to `MainController`.
- Layout: ToolBar (port config) → SplitPane (left status panel / right TabPane HEX+ASCII + send area) → StatusBar.
- Icons via Ikonli Material Design 2 pack (e.g., `mdi2p-power-plug`, `mdi2r-refresh`, `mdi2s-send`).
- CSS class `mono-text-area` applies monospace font + dark background to data display areas.

## Testing

- JUnit 5 (Jupiter) + Mockito.
- Tests live only in `serial-debug-core` (parser + config validation logic).
- UI module has test dependencies but no tests yet (JavaFX UI testing requires TestFX or manual verification).
- Naming convention: `*Test.java`, assertion-first test method names like `shouldEncodeSimpleHex`, `shouldRejectOddLengthHex`.

## Roadmap (from design docs)

- v0.1 ✅ — Basic serial TX/RX + HEX/ASCII dual view (current)
- v0.2 — Waveform chart + log-to-disk
- v0.3 — Config memory + file send
- v0.4 — Data playback + SPI protocol extensions
- v1.0 — i18n + theme switching + jlink packaging
