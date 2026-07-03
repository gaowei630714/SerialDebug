# AT Companion + Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement AT 指令伴侣 (template library + reply highlighting) and 数据仪表盘 (regex-extracted metric cards) as two new lazy tabs in SerialDebug.

**Architecture:** AT 伴侣 uses a `ListView<AtCommand>` with JSON persistence (following `JsonPresetService` pattern) and a `TextFlow` for keyword-colored reply display. 仪表盘 reuses the existing `DataExtractor` from the waveform tab via a new `DashboardConsumer` registered with `SessionDataPipeline`. Both are wired into `SessionTabContent` as lazy tabs.

**Tech Stack:** Java 17, JavaFX 21, Jackson 2.17, Maven multi-module

---

## File Structure

```
serial-debug-ui/src/main/java/io/github/serialdebug/ui/
├── at/
│   ├── AtCompanionPanel.java       # AT 伴侣面板（左模板库 + 右 TextFlow 着色显示）
│   ├── AtCommand.java              # AT 指令模板模型（Jackson POJO）
│   └── AtCommandService.java       # JSON 持久化（~/.serialdebug/at-commands.json）
├── dashboard/
│   ├── DashboardPanel.java         # 仪表盘面板（TilePane 卡片布局）
│   ├── DashboardConsumer.java      # PayloadConsumer → MetricCard 更新
│   └── MetricCard.java             # 数值卡片（latest/min/max/avg/count）
├── controller/
│   ├── SendController.java         # 修改：新增 setSendText() 方法
│   └── DisplayController.java      # 修改：新增 setOnResponseReceived() 回调
├── session/
│   └── SessionTabContent.java      # 修改：替换占位标签页
└── subtab/
    ├── AtConsumer.java             # 删除
    └── DashboardConsumer.java      # 删除
```

---

## Task 1: AtCommand Model

**Files:**
- Create: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCommand.java`

- [ ] **Step 1: Create AtCommand model**

Following the `Preset` pattern (Jackson-compatible mutable POJO with default constructor):

```java
package io.github.serialdebug.ui.at;

/**
 * An AT command template for the AT companion panel.
 * Jackson-compatible POJO (default constructor + getters/setters).
 */
public class AtCommand {

    private String name;
    private String command;
    private String description;

    /** Default constructor required by Jackson. */
    public AtCommand() {
        this("", "", "");
    }

    public AtCommand(String name, String command, String description) {
        this.name = name;
        this.command = command;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return name + "  " + command;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn clean compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCommand.java
git commit -m "feat(ui): add AtCommand model for AT companion"
```

---

## Task 2: AtCommandService (JSON Persistence)

**Files:**
- Create: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCommandService.java`

- [ ] **Step 1: Create AtCommandService**

Following `JsonPresetService` pattern (atomic write, corrupt-file fallback to defaults):

```java
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
            save(defaults); // persist defaults for next launch
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
```

- [ ] **Step 2: Verify compilation**

Run: `mvn clean compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCommandService.java
git commit -m "feat(ui): add AtCommandService with JSON persistence"
```

---

## Task 3: MetricCard Component

**Files:**
- Create: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/dashboard/MetricCard.java`

- [ ] **Step 1: Create MetricCard**

```java
package io.github.serialdebug.ui.dashboard;

import javafx.geometry.Alignment;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * A metric card displaying latest value + Min/Max/Avg statistics.
 * Thread-safe for single-writer (FX thread), single-reader.
 */
public class MetricCard extends VBox {

    private final Label nameLabel;
    private final Label valueLabel;
    private final Label statsLabel;

    private double latest;
    private double min;
    private double max;
    private double sum;
    private int count;

    public MetricCard(String seriesName) {
        setPrefSize(180, 120);
        setMinSize(180, 120);
        setPadding(new Insets(8));
        setSpacing(4);
        getStyleClass().add("metric-card");

        nameLabel = new Label(seriesName);
        nameLabel.getStyleClass().add("metric-card-name");

        valueLabel = new Label("---");
        valueLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 24));
        valueLabel.getStyleClass().add("metric-card-value");

        statsLabel = new Label("Min: ---  Max: ---");
        statsLabel.getStyleClass().add("metric-card-stats");

        Label avgLabel = new Label("Avg: ---  N: 0");
        avgLabel.getStyleClass().add("metric-card-stats");
        this.avgLabel = avgLabel;

        getChildren().addAll(nameLabel, valueLabel, statsLabel, avgLabel);
        setAlignment(Alignment.CENTER_LEFT);
    }

    private final Label avgLabel;

    /** Update card with a new value. Call on FX thread. */
    public synchronized void update(double value) {
        latest = value;
        if (count == 0) {
            min = max = value;
        } else {
            if (value < min) min = value;
            if (value > max) max = value;
        }
        sum += value;
        count++;
        refreshDisplay();
    }

    /** Reset all statistics. */
    public synchronized void reset() {
        latest = 0;
        min = 0;
        max = 0;
        sum = 0;
        count = 0;
        refreshDisplay();
    }

    public synchronized double getLatest() { return latest; }
    public synchronized double getMin() { return min; }
    public synchronized double getMax() { return max; }
    public synchronized double getAverage() { return count > 0 ? sum / count : 0; }
    public synchronized int getCount() { return count; }

    private void refreshDisplay() {
        valueLabel.setText(formatValue(latest));
        statsLabel.setText("Min: " + formatValue(min) + "  Max: " + formatValue(max));
        avgLabel.setText("Avg: " + formatValue(getAverage()) + "  N: " + count);
    }

    /**
     * Format a double value: integer if whole number, 1 decimal otherwise,
     * scientific notation for extreme values, "---" for Infinity/NaN.
     */
    static String formatValue(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "---";
        if (value == Math.rint(value) && Math.abs(value) < 1e6) {
            return String.format("%.0f", value);
        }
        if (Math.abs(value) >= 1e6 || (Math.abs(value) < 0.01 && value != 0)) {
            return String.format("%.2e", value);
        }
        return String.format("%.1f", value);
    }
}
```

- [ ] **Step 2: Add metric card CSS**

Append to `serial-debug-ui/src/main/resources/style.css`:

```css
.metric-card {
    -fx-background-color: #ffffff;
    -fx-border-color: #e0e0e0;
    -fx-border-radius: 6;
    -fx-background-radius: 6;
    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 2, 0, 0, 1);
}
.metric-card-name {
    -fx-font-weight: bold;
    -fx-font-size: 12px;
    -fx-text-fill: #555555;
}
.metric-card-value {
    -fx-text-fill: #2c3e50;
}
.metric-card-stats {
    -fx-font-size: 10px;
    -fx-text-fill: #888888;
    -fx-font-family: "Consolas", "Monaco", "Courier New", monospace;
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/dashboard/MetricCard.java
git add serial-debug-ui/src/main/resources/style.css
git commit -m "feat(ui): add MetricCard component with statistics display"
```

---

## Task 4: DashboardConsumer

**Files:**
- Create: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/dashboard/DashboardConsumer.java`

- [ ] **Step 1: Create DashboardConsumer**

```java
package io.github.serialdebug.ui.dashboard;

import io.github.serialdebug.core.chart.DataExtractor;
import io.github.serialdebug.core.chart.DataExtractor.ExtractedValue;
import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.log.Direction;

import java.util.List;
import java.util.function.Consumer;

/**
 * Consumes raw packets from the pipeline, extracts numeric values using
 * a shared DataExtractor, and forwards them to a DashboardPanel callback.
 */
public class DashboardConsumer implements PayloadConsumer {

    private final DataExtractor extractor;
    private final Consumer<List<ExtractedValue>> onExtracted;

    public DashboardConsumer(DataExtractor extractor,
                             Consumer<List<ExtractedValue>> onExtracted) {
        this.extractor = extractor;
        this.onExtracted = onExtracted;
    }

    @Override
    public void onPacket(RawPacket pkt) {
        if (pkt.dir() == Direction.TX) return;
        String text = new String(pkt.data(), pkt.offset(), pkt.length());
        List<ExtractedValue> values = extractor.extract(text);
        if (!values.isEmpty()) {
            onExtracted.accept(values);
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn clean compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/dashboard/DashboardConsumer.java
git commit -m "feat(ui): add DashboardConsumer for pipeline data extraction"
```

---

## Task 5: DashboardPanel

**Files:**
- Create: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/dashboard/DashboardPanel.java`

- [ ] **Step 1: Create DashboardPanel**

```java
package io.github.serialdebug.ui.dashboard;

import io.github.serialdebug.core.chart.DataExtractor;
import io.github.serialdebug.core.chart.DataExtractor.ExtractedValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard panel: displays metric cards in a TilePane.
 * Reuses a shared DataExtractor instance (same rules as waveform tab).
 */
public class DashboardPanel extends BorderPane {

    private final DataExtractor extractor;
    private final TilePane tilePane;
    private final Map<String, MetricCard> cards = new HashMap<>();

    public DashboardPanel(DataExtractor extractor) {
        this.extractor = extractor;

        Label title = new Label("数据仪表盘 — 数值从串口数据流中提取");
        title.setPadding(new Insets(8, 12, 4, 12));
        title.getStyleClass().add("section-title");
        setTop(title);

        tilePane = new TilePane();
        tilePane.setPadding(new Insets(8));
        tilePane.setHgap(8);
        tilePane.setVgap(8);
        tilePane.setPrefColumns(4);

        ScrollPane scrollPane = new ScrollPane(tilePane);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        setCenter(scrollPane);

        Button clearBtn = new Button("清空统计");
        clearBtn.setOnAction(e -> resetAll());
        clearBtn.setPadding(new Insets(4, 12, 4, 12));
        BorderPane.setAlignment(clearBtn, Pos.CENTER_RIGHT);
        setBottom(clearBtn);
        setPadding(new Insets(0, 0, 8, 0));
    }

    /**
     * Called by DashboardConsumer when new values are extracted.
     * Must be called on FX thread.
     */
    public void onExtracted(List<ExtractedValue> values) {
        for (ExtractedValue v : values) {
            MetricCard card = cards.computeIfAbsent(
                    v.seriesName(), name -> {
                        MetricCard c = new MetricCard(name);
                        tilePane.getChildren().add(c);
                        return c;
                    });
            card.update(v.value());
        }
    }

    /** Reset all card statistics. */
    public void resetAll() {
        cards.values().forEach(MetricCard::reset);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn clean compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/dashboard/DashboardPanel.java
git commit -m "feat(ui): add DashboardPanel with TilePane card layout"
```

---

## Task 6: AtCompanionPanel

**Files:**
- Create: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCompanionPanel.java`

- [ ] **Step 1: Create AtCompanionPanel**

```java
package io.github.serialdebug.ui.at;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.function.Consumer;

/**
 * AT 指令伴侣 panel: left side = template library (ListView + search + add/remove),
 * right side = reply display (TextFlow with keyword coloring).
 */
public class AtCompanionPanel extends BorderPane {

    private static final int MAX_RESPONSE_LINES = 1000;

    private final AtCommandService service;
    private final ObservableList<AtCommand> commands = FXCollections.observableArrayList();
    private final FilteredList<AtCommand> filteredCommands;
    private final Consumer<String> onCommandSelected;

    private final ListView<AtCommand> listView = new ListView<>();
    private final TextFlow responseFlow = new TextFlow();
    private final TextField searchField = new TextField();

    public AtCompanionPanel(Consumer<String> onCommandSelected) {
        this.onCommandSelected = onCommandSelected;
        this.service = new AtCommandService();
        this.filteredCommands = new FilteredList<>(commands);

        // Load commands
        commands.setAll(service.load());

        // Left: template library
        buildLeftPane();

        // Right: response display
        buildRightPane();

        // Wire list selection
        listView.setItems(filteredCommands);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(AtCommand item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setOnMouseClicked(null);
                } else {
                    setText(item.getName() + "  " + item.getCommand());
                    setTooltip(new Tooltip(item.getDescription()));
                    setOnMouseClicked(e -> {
                        if (onCommandSelected != null && item.getCommand() != null) {
                            onCommandSelected.accept(item.getCommand());
                        }
                    });
                }
            }
        });

        // Search filter
        searchField.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.isEmpty()) {
                filteredCommands.setPredicate(null);
            } else {
                String lower = val.toLowerCase();
                filteredCommands.setPredicate(cmd ->
                        cmd.getName().toLowerCase().contains(lower)
                                || cmd.getCommand().toLowerCase().contains(lower));
            }
        });
    }

    private void buildLeftPane() {
        VBox leftPane = new VBox(6);
        leftPane.setPadding(new Insets(8));
        leftPane.setPrefWidth(260);

        Label title = new Label("AT 指令模板库");
        title.getStyleClass().add("section-title");

        searchField.setPromptText("搜索指令...");

        listView.setPrefHeight(400);

        Button addBtn = new Button("+ 添加");
        addBtn.setOnAction(e -> showAddDialog());
        Button delBtn = new Button("- 删除");
        delBtn.setOnAction(e -> {
            AtCommand sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                commands.remove(sel);
                service.save(commands);
            }
        });

        HBox buttons = new HBox(8, addBtn, delBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        leftPane.getChildren().addAll(title, searchField, listView, buttons);
        VBox.setVgrow(listView, Priority.ALWAYS);
        setLeft(leftPane);
    }

    private void buildRightPane() {
        VBox rightPane = new VBox(6);
        rightPane.setPadding(new Insets(8));

        Label title = new Label("模块回复（关键词着色）");
        title.getStyleClass().add("section-title");

        responseFlow.setPadding(new Insets(8));
        responseFlow.setStyle("-fx-background-color: #1e1e1e; -fx-background-radius: 4;");
        responseFlow.setLineSpacing(2);
        responseFlow.setMinHeight(400);
        responseFlow.setPrefWidth(400);

        ScrollPane scrollPane = new ScrollPane(responseFlow);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Button clearBtn = new Button("清空");
        clearBtn.setOnAction(e -> clearResponses());
        HBox btnBox = new HBox(clearBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        rightPane.getChildren().addAll(title, scrollPane, btnBox);
        setCenter(rightPane);
    }

    /**
     * Append a response line with keyword coloring. Call on FX thread.
     */
    public void appendResponse(String text) {
        if (text == null || text.isBlank()) return;

        Text node = new Text(text + "\n");
        node.setFont(javafx.scene.text.Font.font("Consolas", 13));

        // Keyword coloring
        String upper = text.toUpperCase();
        if (upper.contains("+CME ERROR") || upper.contains("+CMS ERROR") || upper.contains("ERROR")) {
            node.setFill(Color.web("#e74c3c"));
        } else if (upper.contains("OK")) {
            node.setFill(Color.web("#2ecc71"));
        } else if (text.trim().startsWith("+")) {
            node.setFill(Color.web("#3498db"));
        } else {
            node.setFill(Color.web("#e0e0e0"));
        }

        responseFlow.getChildren().add(node);

        // Prune old lines
        while (responseFlow.getChildren().size() > MAX_RESPONSE_LINES) {
            responseFlow.getChildren().remove(0);
        }
    }

    /** Clear the response display area. */
    public void clearResponses() {
        responseFlow.getChildren().clear();
    }

    private void showAddDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("添加 AT 指令");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("名称（如：查询信号强度）");
        TextField cmdField = new TextField();
        cmdField.setPromptText("指令（如：AT+CSQ）");
        TextField descField = new TextField();
        descField.setPromptText("说明（可选）");

        VBox content = new VBox(8,
                new Label("名称:"), nameField,
                new Label("指令:"), cmdField,
                new Label("说明:"), descField);
        content.setPadding(new Insets(12));
        dialogPane.setContent(content);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK
                    && !nameField.getText().isBlank()
                    && !cmdField.getText().isBlank()) {
                commands.add(new AtCommand(
                        nameField.getText().trim(),
                        cmdField.getText().trim(),
                        descField.getText().trim()));
                service.save(commands);
            }
        });
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn clean compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCompanionPanel.java
git commit -m "feat(ui): add AtCompanionPanel with template library and reply coloring"
```

---

## Task 7: Add setSendText to SendController

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/SendController.java`

- [ ] **Step 1: Add setSendText method**

Add after `setPortOpen()` method (around line 129):

```java
/** Set the send text field value (used by AT companion to fill commands). */
public void setSendText(String text) {
    if (text != null && sendTextField != null) {
        sendTextField.setText(text);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn clean compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/SendController.java
git commit -m "feat(ui): add setSendText to SendController for AT companion integration"
```

---

## Task 8: Add Response Callback to DisplayController

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/DisplayController.java`

- [ ] **Step 1: Add callback field and setter**

Add after `private Consumer<Boolean> onAutoScrollPaused;` (around line 57):

```java
private Consumer<String> onResponseReceived;
```

Add after `setOnAutoScrollPaused()` method (around line 96):

```java
/** Register a callback for received response text (called on FX thread). */
public void setOnResponseReceived(Consumer<String> callback) {
    this.onResponseReceived = callback;
}
```

- [ ] **Step 2: Invoke callback in flushBatch**

In `flushBatch()`, after the line `asciiViewArea.appendText(...)` (around line 177), inside the for loop, add callback invocation. The callback should be called on FX thread (which `flushBatch` already is). Add after the appendText calls within the loop:

```java
if (onResponseReceived != null) {
    onResponseReceived.accept("[" + e.timestamp + " " + e.dir + "] " + e.ascii);
}
```

The modified for-loop in `flushBatch()` becomes:

```java
for (BatchEntry e : entries) {
    if (hexViewArea.getLength() > 1_000_000) {
        hexViewArea.clear();
        synchronized (bufferLock) { hexLineBuffer.clear(); }
    }
    if (asciiViewArea.getLength() > 1_000_000) {
        asciiViewArea.clear();
        synchronized (bufferLock) { asciiLineBuffer.clear(); }
    }
    hexViewArea.appendText("[" + e.timestamp + " " + e.dir + "] " + e.hex + "\n");
    asciiViewArea.appendText("[" + e.timestamp + " " + e.dir + "] " + e.ascii + "\n");
    if (onResponseReceived != null) {
        onResponseReceived.accept("[" + e.timestamp + " " + e.dir + "] " + e.ascii);
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/DisplayController.java
git commit -m "feat(ui): add response callback to DisplayController for AT companion"
```

---

## Task 9: Wire Tabs in SessionTabContent

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/session/SessionTabContent.java`

- [ ] **Step 1: Add imports**

Add at the top of the file:

```java
import io.github.serialdebug.ui.at.AtCompanionPanel;
import io.github.serialdebug.ui.dashboard.DashboardPanel;
import io.github.serialdebug.ui.dashboard.DashboardConsumer;
```

- [ ] **Step 2: Replace placeholder tabs**

Find and replace the two placeholder tab blocks (around lines 110-114):

Replace:
```java
Tab atTab = new Tab("AT伴侣", createPlaceholder("AT 指令伴侣 — M3"));
subTabs.addLazyTab("at", atTab, new AtConsumer(), null, null);

Tab dashTab = new Tab("仪表盘", createPlaceholder("数据仪表盘 — M3"));
subTabs.addLazyTab("dash", dashTab, new DashboardConsumer(), null, null);
```

With:
```java
// AT companion tab (lazy, no pipeline consumer — receives via DisplayController callback)
AtCompanionPanel atPanel = new AtCompanionPanel(sendController::setSendText);
Tab atTab = new Tab("AT 伴侣", atPanel);
subTabs.addLazyTab("at", atTab, new PayloadConsumer() {
    @Override public void onPacket(RawPacket pkt) { /* AT companion receives via DisplayController callback */ }
}, null, null);

// Dashboard tab (lazy, registers DashboardConsumer)
DashboardPanel dashboardPanel = new DashboardPanel(waveExtractor);
Tab dashTab = new Tab("仪表盘", dashboardPanel);
subTabs.addLazyTab("dash", dashTab,
        new DashboardConsumer(waveExtractor, dashboardPanel::onExtracted),
        null, null);
```

- [ ] **Step 3: Wire DisplayController callback to AT panel**

After `displayController.initialize();` (around line 277), add:

```java
displayController.setOnResponseReceived(atPanel::appendResponse);
```

- [ ] **Step 4: Verify compilation**

Run: `mvn clean compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/session/SessionTabContent.java
git commit -m "feat(ui): wire AT companion and dashboard tabs into SessionTabContent"
```

---

## Task 10: Delete Placeholder Files

**Files:**
- Delete: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/subtab/AtConsumer.java`
- Delete: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/subtab/DashboardConsumer.java`

- [ ] **Step 1: Delete placeholder files**

```bash
git rm serial-debug-ui/src/main/java/io/github/serialdebug/ui/subtab/AtConsumer.java
git rm serial-debug-ui/src/main/java/io/github/serialdebug/ui/subtab/DashboardConsumer.java
```

- [ ] **Step 2: Verify compilation**

Run: `mvn clean compile -pl serial-debug-ui -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git commit -m "chore(ui): remove placeholder AtConsumer and DashboardConsumer"
```

---

## Task 11: Final Verification

- [ ] **Step 1: Full project compilation**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests**

Run: `mvn test`
Expected: All tests pass (no regressions)

- [ ] **Step 3: Verify file structure**

Run: `find serial-debug-ui/src/main/java/io/github/serialdebug/ui/at serial-debug-ui/src/main/java/io/github/serialdebug/ui/dashboard -type f`
Expected output:
```
.../ui/at/AtCommand.java
.../ui/at/AtCommandService.java
.../ui/at/AtCompanionPanel.java
.../ui/dashboard/DashboardConsumer.java
.../ui/dashboard/DashboardPanel.java
.../ui/dashboard/MetricCard.java
```

- [ ] **Step 4: Verify placeholders removed**

Run: `test ! -f serial-debug-ui/src/main/java/io/github/serialdebug/ui/subtab/AtConsumer.java && test ! -f serial-debug-ui/src/main/java/io/github/serialdebug/ui/subtab/DashboardConsumer.java && echo "OK"`
Expected: OK

---

## Self-Review

**Spec coverage:**
- R1 (AT template library + JSON) → Task 1 + Task 2
- R2 (one-click fill send area) → Task 6 (onCommandSelected callback) + Task 7 (setSendText)
- R3 (keyword coloring) → Task 6 (appendResponse with color logic)
- R4 (add/remove commands) → Task 6 (showAddDialog + delete button)
- R5 (search filter) → Task 6 (FilteredList + searchField listener)
- R6 (metric cards with stats) → Task 3 (MetricCard)
- R7 (reuse DataExtractor) → Task 4 + Task 5 (DashboardConsumer + DashboardPanel)
- R8 (clear stats) → Task 5 (resetAll)
- R9 (independent tabs) → Task 9 (two separate lazy tabs)

**Placeholder scan:** No TBDs, no "implement later", no vague references. All code is complete.

**Type consistency:** `AtCommand` POJO matches `Preset` pattern. `DashboardConsumer` matches `ChartConsumer` pattern. `MetricCard.formatValue()` is `static` and testable. Method names consistent across tasks (`setSendText`, `setOnResponseReceived`, `onExtracted`, `appendResponse`).

