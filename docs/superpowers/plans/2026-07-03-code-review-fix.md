# Code Review Fix Plan

> Fix findings from the code review of AT Companion + Dashboard implementation.

**Goal:** Fix 7 issues across Spec (3) and Standards (4) axes.

---

## Task 1: AtCommand → record

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCommand.java`

Change from POJO to Java `record`:

```java
package io.github.serialdebug.ui.at;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AtCommand(
        @JsonProperty("name") String name,
        @JsonProperty("command") String command,
        @JsonProperty("description") String description) {

    @JsonCreator
    public static AtCommand of(
            @JsonProperty("name") String name,
            @JsonProperty("command") String command,
            @JsonProperty("description") String description) {
        return new AtCommand(
                name == null ? "" : name,
                command == null ? "" : command,
                description == null ? "" : description);
    }
}
```

This uses `@JsonCreator` + `@JsonProperty` for Jackson compatibility with records.

---

## Task 2: AtCommandService Interface Segregation

**Files:**
- Create: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCommandService.java` (interface)
- Rename: `.../at/AtCommandService.java` → `.../at/JsonAtCommandService.java`
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCompanionPanel.java` (accept interface via constructor)

### Interface

```java
package io.github.serialdebug.ui.at;

import java.util.List;

public interface AtCommandService {
    List<AtCommand> load();
    void save(List<AtCommand> commands);
}
```

### Rename existing class to JsonAtCommandService implements AtCommandService

- `getDefaults()` stays as static method on `JsonAtCommandService`

### AtCompanionPanel changes

- Constructor changes from `Consumer<String>` to `(AtCommandService service, Consumer<String> onCommandSelected)`
- Callers in `SessionTabContent` pass `new JsonAtCommandService()`

---

## Task 3: MetricCard cleanup

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/dashboard/MetricCard.java`

Changes:
- Remove unused `MAX_RESPONSE_LINES` constant
- Remove `synchronized` from all methods (callers are on FX thread; class Javadoc already says "single-writer (FX thread)")

---

## Task 4: SplitPane layout + default color

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCompanionPanel.java`

Changes:
- Wrap left and right panes in a `SplitPane`, put it in `BorderPane` center
- Change default text color from `#e0e0e0` to spec value `#333333`
- Change response area background from `#1e1e1e` to light (`#f5f5f5`) to match `#333333` text

---

## Task 5: Keyword coloring extraction

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCompanionPanel.java`

Extract if-else chain to a list of (predicate, color) pairs:

```java
private static final List<java.util.function.Function<String, javafx.scene.paint.Color>> COLOR_RULES = List.of(
    text -> { if (text.toUpperCase().contains("+CME ERROR") || text.toUpperCase().contains("+CMS ERROR") || text.toUpperCase().contains("ERROR")) return Color.web("#e74c3c"); return null; },
    text -> { if (text.toUpperCase().contains("OK")) return Color.web("#2ecc71"); return null; },
    text -> { if (text.trim().startsWith("+")) return Color.web("#3498db"); return null; },
    text -> Color.web("#333333")
);
```

This prevents Repeated Switches from growing into a maintenance problem.

---

