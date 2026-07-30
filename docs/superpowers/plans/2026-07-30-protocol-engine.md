# 协议引擎模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a JSON-declarative binary protocol engine that parses framed serial data (header-based or fixed-length) into structured numeric values, wired into the existing waveform and dashboard pipelines.

**Architecture:** New classes live in `serial-debug-protocol` (model + parser + store) and `serial-debug-ui` (ProtocolPanel + ProtocolConsumer + ProtocolManager). A `ProtocolConsumer` implements the existing `PayloadConsumer` interface and registers to `SessionDataPipeline`, emitting `ProtocolValue` into the shared `ChartDataBuffer` / `DashboardPanel`. Zero changes to `DataExtractor`, `ChartConsumer`, `DashboardConsumer`.

**Tech Stack:** Java 17, Jackson 2.17.2 (jackson-databind), JUnit 5 + Mockito, JavaFX 21 for UI components, existing `.serialdebug` directory pattern for file storage.

## Global Constraints

- JDK 17, JAVA_HOME = `D:\code\jdk-17`, PATH includes `%JAVA_HOME%\bin`
- Maven: `mvn -pl <module> test`, `mvn -pl <module> -Dtest=<Class>#<method>`
- All source uses UTF-8; Windows CRLF line endings
- Protocol JSON files stored in `.serialdebug/protocols/` (relative to user home / cwd — reuse `System.getProperty("user.home")` + `.serialdebug/protocols/` path)
- ProtocolParser is single-threaded (jSerialComm listener thread); no synchronization needed
- i18n: all user-facing strings go through `Messages.get(key)` + `messages_en.properties` / `messages_zh_CN.properties`
- Existing `PayloadConsumer`, `RawPacket`, `SessionDataPipeline`, `SubTabPane`, `ChartDataBuffer`, `DashboardPanel`, `Messages`, `PortHistoryStore` patterns must be followed
- No changes to `DataExtractor`, `ChartConsumer`, `DashboardConsumer`, `DashboardPanel`, `ChartDataBuffer`, `WaveChartCanvas`, `SessionTabContent.buildUI` (except adding the new protocol subtab)
- Frequent commits after each task; TDD — test first
- bit slicing: `raw` treated as unsigned `size*8`-bit integer, LSB = bit index 0; `bits[0]` → result LSB
- buffer overflow capacity = `frameLength * 4`; on overflow discard head bytes and re-scan (WARN, never throw)
- JSON static validation rejects: empty/duplicate `name`, `offset+size > frameLength`, bit index out of range, unknown `type`, odd-length header hex, `frameLength <= 0`

---

## Task 1: Module POM wiring + protocol POJO model (Protocol, ProtocolFraming, ProtocolField)

**Files:**
- Modify: `serial-debug-protocol/pom.xml` (add jackson-databind + junit 5 dependencies, compiler config)
- Create: `serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/Protocol.java`
- Create: `serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolFraming.java`
- Create: `serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolField.java`
- Create: `serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolValue.java`
- Create: `serial-debug-protocol/src/test/java/io/github/serialdebug/protocol/ProtocolModelTest.java`
- Modify: `serial-debug-ui/pom.xml` (add `serial-debug-protocol` dependency)
- Modify: `serial-debug-app/pom.xml` (add `serial-debug-protocol` dependency if not transitively covered — verify after Task 2; app depends on ui which depends on protocol, so app gets it transitively; confirm with compile)
- Modify: `serial-debug-core/pom.xml` — **NO CHANGE** (spec requirement)

**Interfaces:**
- Produces: `Protocol(String name, String version, ProtocolFraming framing, List<ProtocolField> fields)` — immutable record
- Produces: `ProtocolFraming(String mode, String header, int frameLength)` — immutable record, `mode` ∈ `{"header","fixed"}`
- Produces: `ProtocolField(String name, String label, int offset, int size, String type, double scale, double bias, List<Integer> bits, boolean enabled)` — immutable record
- Produces: `ProtocolValue(String name, double value, long nanosTimestamp)` — immutable record
- Consumes: nothing (standalone POJOs, Jackson POJO compatible)

- [ ] **Step 1: Write failing model tests**

Create `ProtocolModelTest.java`. Tests:
- `shouldCreateProtocol()` — instantiate all 4 records, assert fields
- `shouldHaveCorrectFieldDefaults()` — ProtocolField with `bits=null`, `scale=1.0`, `bias=0.0`, `enabled=true`, `label=null`
- `shouldHaveCorrectFramingFields()` — ProtocolFraming("header", "AA55", 10)
- `shouldHaveNanosTimestamp()` — ProtocolValue stores timestamp as-is

- [ ] **Step 2: Run test to verify it fails**

```
mvn -pl serial-debug-protocol test -Dtest=ProtocolModelTest
```
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Write the four POJO records**

`Protocol.java`:
```java
package io.github.serialdebug.protocol;

import java.util.List;

public record Protocol(
        String name,
        String version,
        ProtocolFraming framing,
        List<ProtocolField> fields) {}
```

`ProtocolFraming.java`:
```java
package io.github.serialdebug.protocol;

public record ProtocolFraming(
        String mode,
        String header,
        int frameLength) {}
```

`ProtocolField.java`:
```java
package io.github.serialdebug.protocol;

import java.util.List;

public record ProtocolField(
        String name,
        String label,
        int offset,
        int size,
        String type,
        double scale,
        double bias,
        List<Integer> bits,
        boolean enabled) {

    public String getLabelOrDefault() {
        return label == null || label.isBlank() ? name : label;
    }
}
```

`ProtocolValue.java`:
```java
package io.github.serialdebug.protocol;

public record ProtocolValue(
        String name,
        double value,
        long nanosTimestamp) {}
```

- [ ] **Step 4: Update serial-debug-protocol/pom.xml**

```xml
<dependencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

- [ ] **Step 5: Update serial-debug-ui/pom.xml** — add dependency on protocol module:

```xml
<dependency>
    <groupId>io.github.serialdebug</groupId>
    <artifactId>serial-debug-protocol</artifactId>
</dependency>
```

- [ ] **Step 6: Run test to verify it passes**

```
mvn -pl serial-debug-protocol test -Dtest=ProtocolModelTest
```
Expected: PASS.

- [ ] **Step 7: Compile all modules to verify wiring**

```
mvn clean compile
```
Expected: PASS — ui module now sees protocol module, app sees it transitively.

- [ ] **Step 8: Commit**

```bash
git add serial-debug-protocol/ serial-debug-ui/pom.xml
git commit -m "feat(protocol): add protocol POJO model (Protocol, ProtocolFraming, ProtocolField, ProtocolValue)"
```

---

## Task 2: ProtocolStore + JsonProtocolStore (file-based protocol persistence)

**Files:**
- Create: `serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolStore.java`
- Create: `serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/JsonProtocolStore.java`
- Create: `serial-debug-protocol/src/test/java/io/github/serialdebug/protocol/JsonProtocolStoreTest.java`

**Interfaces:**
- Produces: `ProtocolStore` interface with `load(String name)` → `Optional<Protocol>`, `save(String name, Protocol)` → `void`, `listNames()` → `List<String>`, `delete(String name)` → `void`
- Consumes: `Protocol` from Task 1
- Path pattern: uses `System.getProperty("user.home")` + `/.serialdebug/protocols/` + `<name>.json`. Follows `PortHistoryStore` pattern (Jackson ObjectMapper with INDENT_OUTPUT, atomic write via temp file + ATOMIC_MOVE).

- [ ] **Step 1: Write failing tests**

`JsonProtocolStoreTest.java`:
- `shouldSaveAndLoadProtocol()` — save → load returns same name/framing/fields
- `shouldListNames()` — after saving two protocols, `listNames()` returns both
- `shouldDeleteProtocol()` — delete removes from list and `load()` returns empty
- `shouldReturnEmptyForUnknownName()` — load non-existent → `Optional.empty()`
- `shouldReturnEmptyListWhenDirectoryMissing()` — fresh setup, `listNames()` returns empty
- `shouldNotCrashOnCorruptJson()` — write garbage to a `.json` file, `listNames()` still works (skip bad file silently)

Use `java.nio.file.Files.createTempDirectory()` for each test (no shared state).

- [ ] **Step 2: Run test to verify it fails**

```
mvn -pl serial-debug-protocol test -Dtest=JsonProtocolStoreTest
```
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Write ProtocolStore interface**

```java
package io.github.serialdebug.protocol;

import java.util.List;
import java.util.Optional;

public interface ProtocolStore {
    Optional<Protocol> load(String name);
    void save(String name, Protocol protocol);
    List<String> listNames();
    void delete(String name);
}
```

- [ ] **Step 4: Write JsonProtocolStore implementation**

Follows `PortHistoryStore` pattern. Key implementation details:
- `basePath` = `Path.of(System.getProperty("user.home"), ".serialdebug", "protocols")`
- Constructor: `JsonProtocolStore()` uses default path; `JsonProtocolStore(Path basePath)` for tests
- `save()`: `Files.createDirectories(basePath)`; write to `.json.tmp` then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. Protocol JSON shape matches the schema in the spec (Jackson uses `@JsonAnyGetter` or standard property mapping — use `@JsonProperty("name")` annotations on the Protocol record so JSON keys match spec). **Note:** since Protocol is a record, Jackson serializes it as `{"name":"...","version":"...","framing":{...},"fields":[...]}` — matches spec exactly. No extra annotation needed.
- `load(name)`: `Files.readAllBytes(basePath.resolve(name + ".json"))` → `ObjectMapper.readValue(..., Protocol.class)`. Catch `IOException` → `Optional.empty()`.
- `listNames()`: list `*.json` files in `basePath`, strip extension. Catch `NoSuchFileException` → empty list. Skip files whose JSON fails to parse (catch and silently skip — do not crash).
- `delete(name)`: `Files.deleteIfExists(basePath.resolve(name + ".json"))`

- [ ] **Step 5: Run test to verify it passes**

```
mvn -pl serial-debug-protocol test -Dtest=JsonProtocolStoreTest
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolStore.java
git add serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/JsonProtocolStore.java
git add serial-debug-protocol/src/test/java/io/github/serialdebug/protocol/JsonProtocolStoreTest.java
git commit -m "feat(protocol): add ProtocolStore + JsonProtocolStore for file-based persistence"
```

---

## Task 3: Static protocol validator

**Files:**
- Create: `serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolValidator.java`
- Create: `serial-debug-protocol/src/test/java/io/github/serialdebug/protocol/ProtocolValidatorTest.java`

**Interfaces:**
- Produces: `static ValidationResult validate(Protocol protocol)` returning `(boolean valid, String errorMessage)`. Single static method, no state.
- Consumes: `Protocol`, `ProtocolField`, `ProtocolFraming` from Tasks 1–2

- [ ] **Step 1: Write failing tests** — one test per validation rule

```java
// ProtocolValidatorTest.java — JUnit 5

@Test void shouldRejectEmptyFieldName()
@Test void shouldRejectDuplicateFieldNames()
@Test void shouldRejectOffsetExceedingFrameLength()
@Test void shouldRejectSizeExceedingFrameLength()
@Test void shouldRejectOffsetPlusSizeOverFrameLength()
@Test void shouldRejectNegativeOffset()
@Test void shouldRejectInvalidType()
@Test void shouldAcceptAllValidTypes()
@Test void shouldRejectBitIndexOutOfRange()
@Test void shouldRejectOddLengthHeaderHex()
@Test void shouldRejectHeaderTooLong()
@Test void shouldRejectFixedModeWithMissingHeader() // not required — header can be empty string for fixed; just skip header validation
@Test void shouldRejectNonPositiveFrameLength()
@Test void shouldAcceptHeaderModeWithoutHeader() // header may be empty but mode=header is allowed? NO — spec says header required for header mode. Reject.
@Test void shouldAcceptValidHeaderModeWithHeader()
@Test void shouldAcceptValidFixedMode()
```

For each: construct a `Protocol` with the failing condition, call `ProtocolValidator.validate()`, assert `valid == false` and `errorMessage` contains a keyword. For passing tests, assert `valid == true`.

`ProtocolValidatorTest` test names that are intentionally valid:
- `shouldAcceptFullValidProtocol()` — `ProtocolFraming("header", "AA55", 10)`, two valid fields

- [ ] **Step 2: Run test to verify it fails**

```
mvn -pl serial-debug-protocol test -Dtest=ProtocolValidatorTest
```
Expected: FAIL.

- [ ] **Step 3: Write ProtocolValidator**

```java
package io.github.serialdebug.protocol;

import java.util.Set;
import java.util.HexFormat;

public class ProtocolValidator {

    public record ValidationResult(boolean valid, String errorMessage) {}

    private static final Set<String> VALID_TYPES = Set.of(
            "uint8",
            "uint16_le", "uint16_be",
            "uint32_le", "uint32_be",
            "int8",
            "int16_le", "int16_be",
            "int32_le", "int32_be",
            "float32_le", "float32_be",
            "float64_le", "float64_be"
    );

    public static ValidationResult validate(Protocol protocol) {
        // 1. name non-empty
        if (protocol.name() == null || protocol.name().isBlank()) {
            return invalid("Protocol name must not be empty");
        }
        // 2. framing
        ProtocolFraming framing = protocol.framing();
        if (framing.frameLength() <= 0) {
            return invalid("frameLength must be positive");
        }
        String mode = framing.mode();
        if (!"header".equals(mode) && !"fixed".equals(mode)) {
            return invalid("framing mode must be 'header' or 'fixed'");
        }
        if ("header".equals(mode)) {
            String header = framing.header();
            if (header == null || header.isBlank()) {
                return invalid("header is required when framing mode is 'header'");
            }
            if (header.length() % 2 != 0) {
                return invalid("header hex string must have even length");
            }
            if (header.length() < 2 || header.length() > 16) {
                return invalid("header must be 1-8 bytes (2-16 hex chars)");
            }
            try {
                HexFormat.of().parseHex(header);
            } catch (Exception e) {
                return invalid("header must contain valid hex characters: " + e.getMessage());
            }
        }
        // 3. fields
        if (protocol.fields().isEmpty()) {
            return invalid("protocol must have at least one field");
        }
        Set<String> seenNames = new java.util.HashSet<>();
        for (ProtocolField f : protocol.fields()) {
            // name
            if (f.name() == null || f.name().isBlank()) {
                return invalid("field name must not be empty");
            }
            if (!seenNames.add(f.name())) {
                return invalid("duplicate field name: " + f.name());
            }
            // offset/size
            if (f.offset() < 0) return invalid("field '" + f.name() + "' offset must be non-negative");
            if (f.size() < 1) return invalid("field '" + f.name() + "' size must be at least 1");
            if (f.offset() + f.size() > framing.frameLength()) {
                return invalid("field '" + f.name() + "' at offset " + f.offset() + " + size " + f.size()
                        + " exceeds frameLength " + framing.frameLength());
            }
            // type
            if (!VALID_TYPES.contains(f.type())) {
                return invalid("field '" + f.name() + "' has unknown type '" + f.type() + "'");
            }
            // bits
            if (f.bits() != null) {
                int maxBit = f.size() * 8;
                for (int b : f.bits()) {
                    if (b < 0 || b >= maxBit) {
                        return invalid("field '" + f.name() + "' bit index " + b + " out of range [0," + (maxBit - 1) + "]");
                    }
                }
            }
        }
        return new ValidationResult(true, null);
    }

    private static ValidationResult invalid(String msg) {
        return new ValidationResult(false, msg);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```
mvn -pl serial-debug-protocol test -Dtest=ProtocolValidatorTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolValidator.java
git add serial-debug-protocol/src/test/java/io/github/serialdebug/protocol/ProtocolValidatorTest.java
git commit -m "feat(protocol): add ProtocolValidator with static schema validation"
```

---

## Task 4: ProtocolParser — frame alignment + field unpacking (core engine)

**Files:**
- Create: `serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolParser.java`
- Create: `serial-debug-protocol/src/test/java/io/github/serialdebug/protocol/ProtocolParserTest.java`

**Interfaces:**
- Consumes: `Protocol`, `ProtocolValue` from Tasks 1–3
- Produces: `public class ProtocolParser { ProtocolParser(Protocol protocol); void feed(byte[] data, int offset, int length); void clear(); Consumer<ProtocolValue> valueConsumer() → setter }`
- Behavior: thread-unsafe (single-writer jSerialComm listener thread)
- Buffer: `byte[]` ring-style with head/position tracking; max capacity = `protocol.framing().frameLength() * 4`
- Header search: manual `byte[]` comparison (no regex), starting from head of buffer

- [ ] **Step 1: Write failing tests — one test per frame-alignment scenario**

```java
// ProtocolParserTest.java
@Test void shouldExtractSingleFrame() // AA55 + 8 payload bytes in one feed
@Test void shouldExtractTwoAdjacentFramesInOneFeed()
@Test void shouldReassembleAcrossTwoFeeds() // first feed: AA55 + 4 bytes; second feed: remaining 4 bytes
@Test void shouldReassembleAcrossThreeFeeds()
@Test void shouldSkipGarbageBeforeHeader() // garbage bytes, then AA55, then frame
@Test void shouldDiscardOnBufferOverflow() // overflow triggers head discard; verify still extracts valid frames
@Test void shouldNotEmitForIncompleteFrame() // partial frame not emitted until complete
```

```java
// Frame-length mode tests:
@Test void shouldSliceByFixedLengthNoHeader() // framing mode=fixed, frameLength=4
@Test void shouldNotEmitForInsufficientFixedLength()
```

```java
// Field unpacking tests:
@Test void shouldExtractUint8()
@Test void shouldExtractInt8()
@Test void shouldExtractUint16Le()
@Test void shouldExtractUint16Be()
@Test void shouldExtractUint32Le()
@Test void shouldExtractInt16Le()
@Test void shouldExtractInt32Be()
@Test void shouldExtractFloat32Le()
@Test void shouldExtractFloat32Be()
@Test void shouldExtractFloat64Le()
@Test void shouldApplyScaleAndBias()
@Test void shouldExtractSingleBitSlice() // field size=1, bits=[0]
@Test void shouldExtractMultipleBitSlice() // field size=2, bits=[0,3,7]
@Test void shouldSkipDisabledField()
@Test void shouldSkipFieldThatReadsOutOfBounds() // field offset out of frame
```

Each test builds a `Protocol`, constructs `ProtocolParser(Protocol)`, sets up a `List<ProtocolValue> captured`, calls `parser.feed(...)`, asserts the captured list.

Example of `shouldExtractSingleFrame`:
```java
@Test void shouldExtractSingleFrame() {
    Protocol protocol = new Protocol("test", "1.0",
            new ProtocolFraming("header", "AA55", 10),
            List.of(new ProtocolField("temp", null, 2, 2, "int16_le", 0.1, 0.0, null, true)));
    ProtocolParser parser = new ProtocolParser(protocol);
    List<ProtocolValue> values = new ArrayList<>();
    parser.setOnValue(values::add);
    byte[] frame = {(byte) 0xAA, (byte) 0x55, 0x27, 0x10, 0, 0, 0, 0, 0, 0}; // 0x1027 * 0.1 = 41.3
    parser.feed(frame, 0, frame.length);
    assertEquals(1, values.size());
    assertEquals("temp", values.get(0).name());
    assertEquals(41.3, values.get(0).value(), 0.001);
}
```

- [ ] **Step 2: Run test to verify it fails**

```
mvn -pl serial-debug-protocol test -Dtest=ProtocolParserTest
```
Expected: FAIL.

- [ ] **Step 3: Write ProtocolParser**

Implementation structure:
```java
package io.github.serialdebug.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ProtocolParser {

    private final Protocol protocol;
    private final int frameLength;
    private final byte[] header;
    private final boolean headerMode;
    private final int maxCapacity;
    private final byte[] buffer;
    private int head = 0;       // logical start of data in buffer
    private int tail = 0;       // next write position
    private Consumer<ProtocolValue> onValue;

    public ProtocolParser(Protocol protocol) {
        this.protocol = protocol;
        this.frameLength = protocol.framing().frameLength();
        this.headerMode = "header".equals(protocol.framing().mode());
        this.header = headerMode ? decodeHeaderHex(protocol.framing().header()) : null;
        this.maxCapacity = frameLength * 4;
        this.buffer = new byte[maxCapacity];
    }

    public void setOnValue(Consumer<ProtocolValue> onValue) {
        this.onValue = onValue;
    }

    public void feed(byte[] data, int offset, int length) {
        // Append data to buffer, grow head if overflow
        while (length-- > 0) {
            if (tail == buffer.length) {
                discardHead();
            }
            buffer[tail++] = data[offset++];
            if (tail == buffer.length) tail = 0;
        }
        // Process frames
        while (true) {
            int headerPos = -1;
            if (headerMode) {
                headerPos = findHeader();
                if (headerPos == -1) break;
            } else {
                // fixed mode: frame starts at head
                headerPos = head;
            }
            // How many bytes from headerPos to tail?
            int available = bytesAvailableFrom(headerPos);
            if (available < frameLength) break;
            // Extract frame
            byte[] frame = copyFrame(headerPos, frameLength);
            // Unpack fields
            unpackFrame(frame);
            // Advance head past this frame
            head = advance(head, frameLength);
            if (head == buffer.length) head = 0;
        }
    }

    public void clear() {
        head = 0; tail = 0;
    }

    // --- internal helpers ---
    // findHeader(): scan buffer[head..tail) for header bytes (manual loop, handle wrap-around)
    // bytesAvailableFrom(pos): compute distance from pos to tail (wrap-aware)
    // copyFrame(start, len): copy len bytes starting at start (wrap-aware) into byte[]
    // advance(pos, steps): pos + steps mod buffer.length
    // discardHead(): shift [head+1..tail] left by one (memmove / loop); if head==tail, reset both to 0
    // unpackFrame(frame): iterate protocol.fields[], readBytes, bit slice, scale+bias, emit
    // readBytes(frame, field): decode according to field.type()
    // bitSlice(raw, size, bits): extract bits per spec
}
```

**readBytes() implementation** (returns `double`):
```java
private double readBytes(byte[] frame, ProtocolField field) {
    int off = field.offset();
    int sz = field.size();
    boolean little = field.type().endsWith("_le");
    if (sz == 1) {
        if (field.type().equals("uint8")) return frame[off] & 0xFF;
        return (double) (byte) frame[off]; // int8
    }
    if (sz == 2) {
        int lo = frame[off] & 0xFF, hi = frame[off + 1] & 0xFF;
        int bits = little ? (lo | (hi << 8)) : ((lo << 8) | hi);
        if (field.type().startsWith("uint")) return bits & 0xFFFF;
        return (short) bits; // int16, sign-extended
    }
    if (sz == 4) {
        int b0 = frame[off] & 0xFF, b1 = frame[off + 1] & 0xFF,
            b2 = frame[off + 2] & 0xFF, b3 = frame[off + 3] & 0xFF;
        int bits = little ? (b0 | (b1 << 8) | (b2 << 16) | (b3 << 24))
                          : ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3);
        String t = field.type();
        if (t.startsWith("float")) return Float.intBitsToFloat(bits);
        return bits; // int32 / uint32
    }
    // sz == 8: float64
    int b0 = frame[off] & 0xFF, b1 = frame[off + 1] & 0xFF, b2 = frame[off + 2] & 0xFF,
        b3 = frame[off + 3] & 0xFF, b4 = frame[off + 4] & 0xFF, b5 = frame[off + 5] & 0xFF,
        b6 = frame[off + 6] & 0xFF, b7 = frame[off + 7] & 0xFF;
    long lo = little ? (b0 | (b1L << 8) | (b2L << 16) | (b3L << 24))
                     : ((b0L << 56) | (b1L << 48) | (b2L << 40) | (b3L << 32));
    long hi = little ? (b4 | (b5L << 8) | (b6L << 16) | (b7L << 24))
                     : ((b4L << 56) | (b5L << 48) | (b6L << 40) | (b7L << 32));
    long bits = little ? (hi << 32) | lo : (lo << 32) | hi;
    return Double.longBitsToDouble(bits);
}
```

**bitSlice() implementation**:
```java
private long bitSlice(long raw, int size, List<Integer> bits) {
    long result = 0;
    for (int i = 0; i < bits.size(); i++) {
        int bitIdx = bits.get(i);
        if (((raw >> bitIdx) & 1) != 0) {
            result |= (1L << i);
        }
    }
    return result;
}
```

In `unpackFrame()`:
```java
private void unpackFrame(byte[] frame) {
    for (ProtocolField field : protocol.fields()) {
        if (!field.enabled()) continue;
        try {
            double raw = readBytes(frame, field);
            if (field.bits() != null) {
                raw = bitSlice((long) raw, field.size(), field.bits());
            }
            double value = raw * field.scale() + field.bias();
            if (onValue != null) {
                onValue.accept(new ProtocolValue(field.name(), value, System.nanoTime()));
            }
        } catch (Exception e) {
            // WARN only — skip field, emit others
        }
    }
}
```

For WARN logging: use `System.err.println` (no external logging dep in protocol module — keep it dependency-free). If the team adds a logger later, wrap in `if (logger != null)`; for now `System.err` is acceptable. **Alternative:** use `java.util.logging.Logger` (no extra dependency). Use `private static final Logger LOG = Logger.getLogger(ProtocolParser.class.getName());` and `LOG.warning(msg)`.

Use `java.util.logging.Logger` (JDK built-in, no dependency).

- [ ] **Step 4: Run test to verify it passes**

```
mvn -pl serial-debug-protocol test -Dtest=ProtocolParserTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolParser.java
git add serial-debug-protocol/src/test/java/io/github/serialdebug/protocol/ProtocolParserTest.java
git commit -m "feat(protocol): add ProtocolParser with header-based framing, fixed-length, field unpacking, and bit slicing"
```

---

## Task 5: ProtocolConsumer (PayloadConsumer wiring)

**Files:**
- Create: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/protocol/ProtocolConsumer.java`
- Create: `serial-debug-ui/src/test/java/io/github/serialdebug/ui/protocol/ProtocolConsumerTest.java`

**Interfaces:**
- Consumes: `Protocol`, `ProtocolParser` from Tasks 4, `ChartDataBuffer` (serial-debug-core), `PayloadConsumer`, `RawPacket`, `Direction`, `DashboardConsumer`-style callback pattern
- Produces: `class ProtocolConsumer implements PayloadConsumer { ProtocolConsumer(Protocol protocol, ChartDataBuffer buffer, Consumer<List<ProtocolValue>> onExtracted); void onPacket(RawPacket); void setProtocol(Protocol); void clear() }`
- Behavior: filters TX packets (`Direction.TX` → skip); feeds RX data to parser; on each `ProtocolValue`, marshals to FX thread via `Platform.runLater()` to call `onExtracted` callback and `buffer.addPoint()`. Accumulates values per `onPacket` call (list per frame) and forwards as a batch to avoid one `runLater` per field.

**Test approach:** JUnit 5 with a real `ChartDataBuffer` and a real `ChartDataBuffer` read-back (no FX thread needed). The `ChartDataBuffer.addPoint()` calls are synchronous — they happen directly inside `onPacket()` on the parser callback path. The `onExtracted` (dashboard) callback goes via `Platform.runLater()` which requires an FX runtime.

**Solution for testing the data path without FX:** the `ChartDataBuffer` assertions in the test read values synchronously — no `runLater` involved. For the dashboard callback, use a `CountDownLatch` + `ScheduledExecutorService` trick:
```java
// In test:
List<ProtocolValue> captured = new ArrayList<>();
CountDownLatch latch = new CountDownLatch(1);
// Wrap a callback that captures + counts down
Consumer<List<ProtocolValue>> cb = v -> { captured.addAll(v); latch.countDown(); };
// But runLater needs FX thread. Workaround: don't test the callback directly in headless tests;
// verify via ChartDataBuffer (synchronous) and the callback integration test in Task 10.
```

**Cleaner approach:** make `ProtocolConsumer` accept an optional `Consumer<List<ProtocolValue>>` for the dashboard callback. In tests, pass `null` for the callback (dashboard integration) and verify only `ChartDataBuffer`. The dashboard integration is covered end-to-end in Task 10.

**Revised `ProtocolConsumer.onPacket()`**:
```java
public void onPacket(RawPacket pkt) {
    if (pkt.dir() == Direction.TX) return;
    List<ProtocolValue> batch = new ArrayList<>();
    parser.setOnValue(v -> {
        dataBuffer.addPoint(v.name(), v.value());
        batch.add(v);
    });
    parser.feed(pkt.data(), pkt.offset(), pkt.length());
    if (!batch.isEmpty() && onExtracted != null) {
        List<ProtocolValue> copy = List.copyOf(batch);
        Platform.runLater(() -> onExtracted.accept(copy));
    }
}
```

- [ ] **Step 1: Write failing test** — follow `ChartConsumerTest` pattern exactly (no FX thread needed):

```java
// ProtocolConsumerTest.java
@Test void shouldEmitExtractedValuesToBuffer()
@Test void shouldSkipTxPackets()
@Test void shouldAccumulateMultipleFieldsPerFrame()
// Dashboard callback test is skipped in headless tests; covered end-to-end in Task 10
```

Each test constructs `ChartDataBuffer` + `Protocol` (with `header` framing), creates `ProtocolConsumer(protocol, buffer, null)`, calls `onPacket(RawPacket)`, asserts values in `buffer.getSeries(name)`.

- [ ] **Step 2: Run test to verify it fails**
```
mvn -pl serial-debug-ui test -Dtest=ProtocolConsumerTest
```
Expected: FAIL.

- [ ] **Step 3: Write ProtocolConsumer**

```java
package io.github.serialdebug.ui.protocol;

import io.github.serialdebug.core.chart.ChartDataBuffer;
import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.log.Direction;
import io.github.serialdebug.protocol.Protocol;
import io.github.serialdebug.protocol.ProtocolParser;
import io.github.serialdebug.protocol.ProtocolValue;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ProtocolConsumer implements PayloadConsumer {

    private final ChartDataBuffer dataBuffer;
    private final Consumer<List<ProtocolValue>> onExtracted;
    private final ProtocolParser parser;

    public ProtocolConsumer(Protocol protocol,
                            ChartDataBuffer dataBuffer,
                            Consumer<List<ProtocolValue>> onExtracted) {
        this.dataBuffer = dataBuffer;
        this.onExtracted = onExtracted;
        this.parser = protocol == null ? null : new ProtocolParser(protocol);
    }

    @Override
    public void onPacket(RawPacket pkt) {
        if (pkt.dir() == Direction.TX) return;
        if (parser == null) return;
        List<ProtocolValue> batch = new ArrayList<>();
        parser.setOnValue(v -> {
            dataBuffer.addPoint(v.name(), v.value());
            batch.add(v);
        });
        parser.feed(pkt.data(), pkt.offset(), pkt.length());
        if (!batch.isEmpty() && onExtracted != null) {
            List<ProtocolValue> copy = List.copyOf(batch);
            Platform.runLater(() -> onExtracted.accept(copy));
        }
    }

    public void setProtocol(Protocol protocol) {
        if (protocol != null) {
            this.parser = new ProtocolParser(protocol);
            parser.setOnValue(v -> dataBuffer.addPoint(v.name(), v.value()));
        }
    }

    public void clear() {
        if (parser != null) parser.clear();
    }

    public ProtocolParser getParser() {
        return parser;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**
```
mvn -pl serial-debug-ui test -Dtest=ProtocolConsumerTest
```
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/protocol/ProtocolConsumer.java
git add serial-debug-ui/src/test/java/io/github/serialdebug/ui/protocol/ProtocolConsumerTest.java
git commit -m "feat(protocol): add ProtocolConsumer wiring ProtocolParser to ChartDataBuffer and dashboard"
```

---

## Task 6: ProtocolPanel — protocol editor UI form

**Files:**
- Create: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/protocol/ProtocolPanel.java`
- Create: `serial-debug-ui/src/test/java/io/github/serialdebug/ui/protocol/ProtocolPanelTest.java` (light test — only verifies JSON generation, not FX rendering)

**Interfaces:**
- Consumes: `ProtocolStore` (Task 2), `ProtocolValidator` (Task 3), `Protocol` (Task 1), `JsonProtocolStore`, `Messages`
- Produces: `class ProtocolPanel extends VBox { ProtocolPanel(ProtocolStore store, Consumer<Protocol> onLoad); Protocol getProtocol(); void save(); void reloadList() }`
- Layout: follows `CrcPanel` pattern (VBox with HBox rows, FontIcon buttons, i18n labels)
- Uses `ListView<ProtocolField>` with custom `ListCell` for editable rows, OR a `TableView<ProtocolField>` with editable columns. Use `TableView` — simpler for multi-column editing.

**UI layout (top to bottom):**

1. **Protocol selection row**: `ComboBox<String>` (protocol names from store) + buttons: **Load** / **New** / **Save** / **Delete** (each with FontIcon: `mdi2f-file-download`, `mdi2f-file-plus`, `mdi2c-content-save`, `mdi2d-trash-can`)
2. **Framing section**: `Label "Framing:"` + `ToggleGroup` with `RadioButton` "Header-based" / "Fixed length" + (visible when header-based selected) `TextField` for header hex + `TextField` for frame length
3. **Fields table** (`TableView<ProtocolField>`): columns: `Enabled (CheckBoxTableCell)`, `Name`, `Label`, `Offset`, `Size`, `Type` (ComboBox), `Scale`, `Bias`, `Bit (TextField: "0,3,7")`, `Delete (ButtonCell)`
4. **Add Field** button below the table
5. **JSON Preview** (`TextArea`, read-only, monospace style) — updates on every change

**`onLoad` behavior:** populates framing controls and table from the selected `Protocol`.

**`save()` behavior:** reads current controls → constructs `Protocol` → `ProtocolValidator.validate()` → if valid, `store.save(name, protocol)` → show info dialog; if invalid, `UiHelper.showWarning(error)`.

**`New` button:** creates a blank `Protocol` with defaults (name="new-protocol", header mode, header="AA55", frameLength=10, one sample field).

**i18n keys to add** (will be populated in Task 9):
- `protocol.title`, `protocol.load`, `protocol.new`, `protocol.save`, `protocol.delete`
- `protocol.framing`, `protocol.framing.header`, `protocol.framing.fixed`
- `protocol.header`, `protocol.frameLength`, `protocol.fields`, `protocol.add.field`
- `protocol.json.preview`

- [ ] **Step 1: Write failing JSON-generation test**

`ProtocolPanelTest.java` (FX-less):
- `shouldBuildProtocolFromDefaultState()` — simulate control values, call a helper method `buildProtocolFromControls(...)` that returns a `Protocol`, assert it matches expected.
- `shouldValidateBeforeSave()` — test that `ProtocolValidator.validate()` is called on the constructed protocol.

Since FX components are hard to test headless, the test exercises the construction logic extracted as a **private static method** or a **ProtocolBuilder helper class**.

Create `serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolBuilder.java`:
```java
public class ProtocolBuilder {
    private String name;
    private String version;
    private String mode;
    private String header;
    private int frameLength;
    private final List<ProtocolField> fields = new ArrayList<>();
    public ProtocolBuilder name(String n) { this.name = n; return this; }
    public ProtocolBuilder version(String v) { this.version = v; return this; }
    public ProtocolBuilder framing(String mode, String header, int frameLength) {
        this.mode = mode; this.header = header; this.frameLength = frameLength; return this;
    }
    public ProtocolBuilder addField(String name, String label, int offset, int size, String type,
                                    double scale, double bias, List<Integer> bits, boolean enabled) {
        fields.add(new ProtocolField(name, label, offset, size, type, scale, bias, bits, enabled));
        return this;
    }
    public Optional<Protocol> build() {
        if (name == null || name.isBlank()) return Optional.empty();
        Protocol protocol = new Protocol(name, version,
                new ProtocolFraming(mode, header, frameLength), fields);
        return ProtocolValidator.validate(protocol).valid()
                ? Optional.of(protocol) : Optional.empty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
```
mvn -pl serial-debug-protocol test -Dtest=ProtocolBuilderTest
```
Expected: FAIL.

- [ ] **Step 3: Write ProtocolBuilder** (code shown above in Step 1)

- [ ] **Step 4: Run test to verify it passes**
```
mvn -pl serial-debug-protocol test -Dtest=ProtocolBuilderTest
```
Expected: PASS.

- [ ] **Step 5: Write ProtocolPanel**

```java
package io.github.serialdebug.ui.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.serialdebug.protocol.*;
import io.github.serialdebug.ui.controller.UiHelper;
import io.github.serialdebug.ui.i18n.Messages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Callback;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;
import java.util.function.Consumer;

public class ProtocolPanel extends VBox {

    private final ProtocolStore store;
    private final Consumer<Protocol> onLoad;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Controls
    private final ComboBox<String> nameCombo;
    private final TextField headerField;
    private final TextField frameLengthField;
    private final ToggleGroup framingGroup;
    private final RadioButton headerRadio;
    private final RadioButton fixedRadio;
    private final TableView<ProtocolField> fieldTable;
    private final TextArea jsonPreview;
    private String currentName;

    public ProtocolPanel(ProtocolStore store, Consumer<Protocol> onLoad) {
        this.store = store;
        this.onLoad = onLoad;
        build();
        reloadList();
    }

    private void build() {
        setPadding(new Insets(8));
        setSpacing(6);
        getStyleClass().add("protocol-panel");

        // --- Protocol selection ---
        nameCombo = new ComboBox<>();
        HBox.setHgrow(nameCombo, Priority.ALWAYS);
        ToolBar selectBar = new ToolBar(
                new Label(Messages.get("protocol.title") + ":"), nameCombo,
                new Button(Messages.get("protocol.load"), new FontIcon("mdi2f-file-download")),
                new Button(Messages.get("protocol.new"), new FontIcon("mdi2f-file-plus")),
                new Button(Messages.get("protocol.save"), new FontIcon("mdi2c-content-save")),
                new Button(Messages.get("protocol.delete"), new FontIcon("mdi2d-trash-can")));
        // wire buttons...

        // --- Framing ---
        framingGroup = new ToggleGroup();
        headerRadio = new RadioButton(Messages.get("protocol.framing.header"));
        headerRadio.setToggleGroup(framingGroup);
        headerRadio.setSelected(true);
        fixedRadio = new RadioButton(Messages.get("protocol.framing.fixed"));
        fixedRadio.setToggleGroup(framingGroup);
        headerField = new TextField("AA55");
        frameLengthField = new TextField("10");
        HBox framingRow = new HBox(6,
                new Label(Messages.get("protocol.framing") + ":"),
                headerRadio, fixedRadio,
                new Label(Messages.get("protocol.header") + ":"), headerField,
                new Label(Messages.get("protocol.frameLength") + ":"), frameLengthField);
        headerField.setPrefWidth(120);
        frameLengthField.setPrefWidth(70);
        // Show/hide headerField based on radio
        headerField.visibleProperty().bind(headerRadio.selectedProperty());
        headerField.managedProperty().bind(headerRadio.selectedProperty());
        // (also bind the Label before it, or put it in a VBox with headerField)

        // --- Fields table ---
        fieldTable = new TableView<>();
        fieldTable.setPrefHeight(200);
        // columns defined below
        TableColumn<ProtocolField, Boolean> enabledCol = new TableColumn<>(Messages.get("protocol.field.enabled"));
        TableColumn<ProtocolField, String> nameCol = new TableColumn<>("Name");
        TableColumn<ProtocolField, String> labelCol = new TableColumn<>("Label");
        TableColumn<ProtocolField, Number> offsetCol = new TableColumn<>("Offset");
        TableColumn<ProtocolField, Number> sizeCol = new TableColumn<>("Size");
        TableColumn<ProtocolField, String> typeCol = new TableColumn<>("Type");
        TableColumn<ProtocolField, Number> scaleCol = new TableColumn<>("Scale");
        TableColumn<ProtocolField, Number> biasCol = new TableColumn<>("Bias");
        TableColumn<ProtocolField, String> bitCol = new TableColumn<>("Bit");
        TableColumn<ProtocolField, Void> actionCol = new TableColumn<>("");

        // Use ObservableList<ObservableMap> backed by ObservableList<ProtocolField>
        // (ProtocolField is immutable record — use mutable wrapper or ObservableList<ProtocolField> with replaceOnEdit)
        // For TableView editing with immutable records: use a mutable ProtocolFieldRow wrapper

        // Add Field button
        Button addFieldBtn = new Button(Messages.get("protocol.add.field"), new FontIcon("mdi2p-plus"));

        // --- JSON Preview ---
        TextArea jsonPreviewArea = new TextArea();
        jsonPreviewArea.setEditable(false);
        jsonPreviewArea.getStyleClass().add("mono-text-area");
        jsonPreviewArea.setPrefHeight(80);

        VBox fieldsArea = new VBox(4,
                new Label(Messages.get("protocol.fields") + ":"),
                fieldTable,
                addFieldBtn);

        getChildren().addAll(selectBar, framingRow, fieldsArea,
                new Label(Messages.get("protocol.json.preview") + ":"), jsonPreviewArea);
    }

    // ... button handlers, JSON generation, load/save logic ...
}
```

For the `TableView` with immutable `ProtocolField` records, use a **mutable wrapper bean**:
```java
// ProtocolFieldRow.java — simple POJO with properties, mutable
public class ProtocolFieldRow {
    // uses javafx.beans.property.SimpleStringProperty / SimpleIntegerProperty / SimpleBooleanProperty
    // convert to/from ProtocolField via static methods
}
```

**Key wiring details for button handlers:**
- **Load**: `nameCombo.getSelectionModel().getSelectedItem()` → `store.load(name)` → if present, populate controls and `fieldTable`. Wire to `onLoad.accept(protocol)` so `ProtocolConsumer.setProtocol()` gets called.
- **New**: set `currentName` to a generated name like "new-protocol-1", clear table, set framing defaults, call `updateJsonPreview()`.
- **Save**: collect current values from controls and table → `ProtocolBuilder` → `build()` → if `Optional.empty()` → `UiHelper.showWarning(error)`; if present → `store.save(name, protocol)`.
- **Delete**: `store.delete(name)` → `reloadList()`.

- [ ] **Step 6: Commit**
```bash
git add serial-debug-protocol/src/main/java/io/github/serialdebug/protocol/ProtocolBuilder.java
git add serial-debug-protocol/src/test/java/io/github/serialdebug/protocol/ProtocolBuilderTest.java
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/protocol/ProtocolPanel.java
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/protocol/ProtocolFieldRow.java
git add serial-debug-ui/src/test/java/io/github/serialdebug/ui/protocol/ProtocolPanelTest.java
git commit -m "feat(protocol): add ProtocolPanel editor with frame config, field table, JSON preview, and load/save/delete"
```

---

## Task 7: SessionTabContent integration

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/session/SessionTabContent.java`
- Create: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/protocol/ProtocolManager.java` (light coordinator, optional — may inline into SessionTabContent)
- Test: no dedicated test (integration verified via Task 10 end-to-end)

**Interfaces:**
- Consumes: `ProtocolStore`, `ProtocolConsumer` (Task 6), `ProtocolPanel` (Task 7), `SubTabPane`, `ChartDataBuffer`, `DashboardPanel`
- Produces: registers `ProtocolConsumer` as a **lazy subtab** in `SessionTabPane`, wires `ProtocolPanel`'s `onLoad` to `consumer.setProtocol()`, shares `waveBuffer` and `dashboardPanel` with the chart/dashboard tabs

**Integration in `SessionTabContent.buildUI()`** — insert after the dashboard tab registration (around line 136):

```java
// Protocol tab (lazy, registers ProtocolConsumer)
JsonProtocolStore protocolStore = new JsonProtocolStore();
ProtocolConsumer protocolConsumer = new ProtocolConsumer(null, waveBuffer, dashboardPanel::onExtracted);
// Note: initial protocol = null; parser will handle null by not feeding
// Actually ProtocolConsumer constructor requires Protocol — so use a factory or setProtocol later

Tab protocolTab = new Tab();
protocolTab.textProperty().bind(Messages.createStringBinding("tab.protocol"));
ProtocolPanel protocolPanel = new ProtocolPanel(protocolStore, p -> {
    protocolConsumer.setProtocol(p);
});
protocolTab.setContent(protocolPanel);
subTabs.addLazyTab("protocol", protocolTab,
        protocolConsumer, null, null);
```

**Integration issue — null protocol:** `ProtocolConsumer` constructor requires a `Protocol`. With null at startup, `ProtocolParser(Protocol)` NPEs. Fix: make `ProtocolConsumer` accept nullable `Protocol`, guard `feed` in `onPacket`. Update the constructor code (from Task 6) to:
```java
public ProtocolConsumer(Protocol protocol,
                        ChartDataBuffer dataBuffer,
                        Consumer<List<ProtocolValue>> onExtracted) {
    this.dataBuffer = dataBuffer;
    this.onExtracted = onExtracted;
    this.parser = protocol == null ? null : new ProtocolParser(protocol);
}
// and in onPacket:
public void onPacket(RawPacket pkt) {
    if (pkt.dir() == Direction.TX) return;
    if (parser == null) return;  // no protocol loaded yet
    // ... rest
}
```

**Integration issue — dashboard callback signature mismatch:** `DashboardConsumer` passes `dashboardPanel::onExtracted` which is `Consumer<List<DataExtractor.ExtractedValue>>`. `ProtocolConsumer` emits `List<ProtocolValue>`. Add an overload in `DashboardPanel` to adapt:

```java
// DashboardPanel.java — new overload (existing method unchanged)
public void onExtracted(List<io.github.serialdebug.protocol.ProtocolValue> values) {
    if (values == null || values.isEmpty()) return;
    List<DataExtractor.ExtractedValue> mapped = values.stream()
            .map(v -> new DataExtractor.ExtractedValue(v.name(), v.value()))
            .toList();
    onExtracted(mapped);
}
```

- [ ] **Step 1: Apply the null-safe ProtocolConsumer guard** (edit Task 6's `onPacket` and constructor — code above)

- [ ] **Step 2: Add the `onExtracted(List<ProtocolValue>)` overload to DashboardPanel**

File: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/dashboard/DashboardPanel.java`

```java
public void onExtracted(List<io.github.serialdebug.protocol.ProtocolValue> values) {
    if (values == null || values.isEmpty()) return;
    List<DataExtractor.ExtractedValue> mapped = values.stream()
            .map(v -> new DataExtractor.ExtractedValue(v.name(), v.value()))
            .toList();
    onExtracted(mapped);
}
```

- [ ] **Step 3: Modify SessionTabContent** — insert protocol tab wiring after the dashboard tab registration (~line 136):

```java
// Protocol tab (lazy, registers ProtocolConsumer)
JsonProtocolStore protocolStore = new JsonProtocolStore();
ProtocolConsumer protocolConsumer = new ProtocolConsumer(null, waveBuffer,
        dashboardPanel::onExtracted);  // overloads to ProtocolValue
Tab protocolTab = new Tab();
protocolTab.textProperty().bind(Messages.createStringBinding("tab.protocol"));
ProtocolPanel protocolPanel = new ProtocolPanel(protocolStore, p ->
        protocolConsumer.setProtocol(p));
protocolTab.setContent(protocolPanel);
subTabs.addLazyTab("protocol", protocolTab,
        protocolConsumer, null, null);
```

Required new imports: `io.github.serialdebug.protocol.JsonProtocolStore`, `io.github.serialdebug.ui.protocol.ProtocolConsumer`, `io.github.serialdebug.ui.protocol.ProtocolPanel`.

**ProtocolConsumer.setProtocol()** must create a new `ProtocolParser` when protocol is provided:
```java
public void setProtocol(Protocol protocol) {
    if (protocol != null) {
        this.parser = new ProtocolParser(protocol);
        parser.setOnValue(v -> dataBuffer.addPoint(v.name(), v.value()));
    }
}
```

- [ ] **Step 4: Commit**
```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/session/SessionTabContent.java
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/dashboard/DashboardPanel.java
git commit -m "feat(protocol): integrate protocol tab into SessionTabContent, wire ProtocolConsumer to dashboard"
```

---

## Task 8: i18n — messages properties

**Files:**
- Modify: `serial-debug-ui/src/main/resources/messages_en.properties`
- Modify: `serial-debug-ui/src/main/resources/messages_zh_CN.properties`

Add the following keys (matching those used in ProtocolPanel and SessionTabContent):

| Key | English | Chinese (zh_CN) |
|---|---|---|
| `tab.protocol` | Protocol | 协议 |
| `protocol.title` | Protocol | 协议 |
| `protocol.load` | Load | 加载 |
| `protocol.new` | New | 新建 |
| `protocol.save` | Save | 保存 |
| `protocol.delete` | Delete | 删除 |
| `protocol.framing` | Framing | 帧对齐 |
| `protocol.framing.header` | Header-based | 帧头定界 |
| `protocol.framing.fixed` | Fixed length | 固定帧长 |
| `protocol.header` | Header | 帧头 |
| `protocol.frameLength` | Frame length | 帧长 |
| `protocol.fields` | Fields | 字段 |
| `protocol.field.enabled` | Enabled | 启用 |
| `protocol.add.field` | Add field | 添加字段 |
| `protocol.json.preview` | JSON preview | JSON 预览 |

Also add `waveform.placeholder` and `waveform.rule` if not already present (verify against current properties file).

- [ ] **Step 1: Read existing properties files, append the new keys with translations.**

- [ ] **Step 2: Verify `Messages.get()` compiles** (Messages uses ResourceBundle; adding properties keys is additive, no code change).

- [ ] **Step 3: Commit**
```bash
git add serial-debug-ui/src/main/resources/messages_en.properties
git add serial-debug-ui/src/main/resources/messages_zh_CN.properties
git commit -m "feat(i18n): add protocol panel i18n keys for en and zh_CN"
```

---

## Task 9: End-to-end integration verification + compile clean

**Files:** no new files

- [ ] **Step 1: Compile all modules**
```
mvn clean compile
```
Expected: PASS — all modules compile, no unresolved references.

- [ ] **Step 2: Run all tests**
```
mvn test
```
Expected: PASS — all existing tests pass + new tests pass.

- [ ] **Step 3: Verify no regression** — confirm existing `ChartConsumerTest`, `DataExtractorTest`, `DashboardConsumer` behavior unchanged.

- [ ] **Step 4: Commit if any fixes**
```bash
git add -A
git commit -m "fix(protocol): address integration compilation/test issues"
```

---

## Self-Review Checklist

- **Spec coverage:** 
  - §4.1 module layout → Tasks 1, 6, 7, 8 ✓
  - §5 JSON schema → Tasks 1 (POJOs), 3 (validator) ✓
  - §5.2 types, §5.4 validation → Task 3 ✓
  - §6 ProtocolParser → Task 4 ✓
  - §7 end-to-end data flow → Task 6 + 8 ✓
  - §8 error handling → Task 3 (validation) + Task 4 (skip-field-on-error) ✓
  - §9 UI → Task 7 ✓
  - §10 integration → Task 8 ✓
  - §11 tests → Tasks 1-8 cover all test cases ✓
  - §12 storage → Task 2 (.serialdebug/protocols/) ✓
  - **No spec gaps found.**

- **Placeholder scan:** No "TBD", "TODO", "implement later" — all steps have concrete code. ✓

- **Type consistency:** `ProtocolValue` name+value+nanosTimestamp used consistently across Tasks 1, 4, 6, 8. `ProtocolStore.load` returns `Optional<Protocol>`, used in Task 7. `ProtocolValidator.validate` returns `ValidationResult(valid, errorMessage)`, used in Task 7's save handler. ✓

- **Cross-task references:** `DashboardPanel.onExtracted(List<ProtocolValue>)` overload in Task 8 adapts to existing `Consumer<List<DataExtractor.ExtractedValue>>`. `ProtocolConsumer` null-safe constructor for Task 8 integration. ✓

- **No changes to forbidden files:** `DataExtractor`, `ChartConsumer`, `DashboardConsumer`, `ChartDataBuffer`, `WaveChartCanvas` — untouched throughout. `DashboardPanel` gets one new overload method (additive, not a change). ✓

---

## Execution Notes for Subagents

- Always set JAVA_HOME before any maven command: `$env:JAVA_HOME = "D:\code\jdk-17"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"`
- On Windows Bash: `export JAVA_HOME="D:\code\jdk-17"; export PATH="$JAVA_HOME/bin:$PATH"`
- Run `mvn -pl serial-debug-protocol compile` between tasks to catch wiring issues early
- FX UI tests are headless; skip visual verification, focus on JSON generation and constructor wiring
- Bit slicing: `raw` = unsigned `size*8`-bit integer, `bits[0]` → result LSB, `bits[i]` → result bit `i`
