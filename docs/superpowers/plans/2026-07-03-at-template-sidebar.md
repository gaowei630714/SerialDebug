# AT Template Sidebar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move AT template list from separate sub-tab to inline sidebar in IO tab's send area.

**Architecture:** Remove the AT companion tab and its response display. Inline a compact AT template sidebar (search + ListView + add button) into the IO tab's send area BorderPane, toggled by a toolbar button. Rollback the DisplayController.onResponseReceived callback since AT no longer needs separate response data.

**Tech Stack:** Java 17, JavaFX 21

---

## Task 1: Rollback DisplayController

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/controller/DisplayController.java`

Remove `onResponseReceived` field (line ~58), `setOnResponseReceived()` setter (line ~100), and the callback invocation in `flushBatch()` (line ~185).

After removal, these should no longer appear in the file:
```java
private Consumer<String> onResponseReceived;
public void setOnResponseReceived(Consumer<String> callback) { ... }
if (onResponseReceived != null) { onResponseReceived.accept(...); }
```

- [ ] Read current DisplayController.java
- [ ] Remove the 3 pieces of code (field, setter, invocation)
- [ ] Verify compilation: `mvn clean compile -pl serial-debug-ui -am`
- [ ] Commit: `git add -A && git commit -m "fix(ui): rollback onResponseReceived callback from DisplayController"`

---

## Task 2: AT Sidebar in IO Tab + Remove AT Tab

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/session/SessionTabContent.java`

Changes in `createIOView()`:

1. **Add AT toggle button** to the search ToolBar:
```java
ToggleButton atToggle = new ToggleButton("AT");
atToggle.setTooltip(new Tooltip("AT 指令模板"));
```

2. **Build AT sidebar VBox** (~70 lines) after sendArea construction:
```java
VBox atSidebar = new VBox(6);
atSidebar.setPrefWidth(220);
atSidebar.setMinWidth(220);
atSidebar.setPadding(new Insets(6));
atSidebar.setStyle("-fx-border-color: #ddd; -fx-border-width: 0 0 0 1;");
atSidebar.setVisible(false);
atSidebar.managedProperty().bind(atSidebar.visibleProperty());

Label atTitle = new Label("AT 指令模板");
atTitle.getStyleClass().add("section-title");

TextField atSearch = new TextField();
atSearch.setPromptText("搜索指令...");

AtCommandService atService = new JsonAtCommandService();
ObservableList<AtCommand> atCommands = FXCollections.observableArrayList();
FilteredList<AtCommand> atFiltered = new FilteredList<>(atCommands);
atCommands.setAll(atService.load());

ListView<AtCommand> atList = new ListView<>(atFiltered);
atList.setPrefHeight(200);
atList.setCellFactory(lv -> new ListCell<>() {
    @Override
    protected void updateItem(AtCommand item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setOnMouseClicked(null);
        } else {
            setText(item.name() + "  " + item.command());
            setTooltip(new Tooltip(item.description()));
            setOnMouseClicked(e -> sendController.setSendText(item.command()));
        }
    }
});

atSearch.textProperty().addListener((obs, old, val) -> {
    if (val == null || val.isEmpty()) {
        atFiltered.setPredicate(null);
    } else {
        String lower = val.toLowerCase();
        atFiltered.setPredicate(cmd ->
            cmd.name().toLowerCase().contains(lower)
                || cmd.command().toLowerCase().contains(lower));
    }
});

Button atAddBtn = new Button("+ 添加");
// add button dialog same as current AtCompanionPanel.showAddDialog()
Button atDelBtn = new Button("- 删除");
atDelBtn.setOnAction(e -> {
    AtCommand sel = atList.getSelectionModel().getSelectedItem();
    if (sel != null) { atCommands.remove(sel); atService.save(atCommands); }
});

HBox atButtons = new HBox(6, atAddBtn, atDelBtn);
atSidebar.getChildren().addAll(atTitle, atSearch, atList, atButtons);

atToggle.selectedProperty().addListener((obs, old, val) -> atSidebar.setVisible(val));
```

3. **Wrap sendArea + atSidebar in BorderPane**:
Replace:
```java
view.getChildren().addAll(displayTabs, searchBar, new Separator(), sendArea);
```
With:
```java
BorderPane bottomArea = new BorderPane();
bottomArea.setCenter(sendArea);
bottomArea.setRight(atSidebar);
Separator sep = new Separator();
view.getChildren().addAll(displayTabs, searchBar, sep, bottomArea);
```

4. **Add imports** at top of file:
```java
import io.github.serialdebug.ui.at.AtCommand;
import io.github.serialdebug.ui.at.AtCommandService;
import io.github.serialdebug.ui.at.JsonAtCommandService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
```

5. **Remove AT companion tab registration** — delete the block:
```java
// AT companion tab (added after sendController is initialized)
AtCompanionPanel atPanel = new AtCompanionPanel(...);
displayController.setOnResponseReceived(atPanel::appendResponse);
Tab atTab = new Tab("AT 伴侣", atPanel);
subTabs.addLazyTab("at", atTab, new PayloadConsumer() {...}, null, null);
```

- [ ] Read current SessionTabContent.java createIOView() method
- [ ] Add imports for AT classes + FXCollections + FilteredList
- [ ] Add AT toggle button to search bar
- [ ] Build AT sidebar VBox after sendArea construction
- [ ] Wrap sendArea + atSidebar in BorderPane
- [ ] Remove AT companion tab registration block
- [ ] Verify compilation: `mvn clean compile -pl serial-debug-ui -am`
- [ ] Commit: `git add -A && git commit -m "feat(ui): integrate AT template sidebar into IO tab, remove separate AT tab"`

---

## Task 3: Delete AtCompanionPanel

**Files:**
- Delete: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCompanionPanel.java`

- [ ] Remove file: `git rm serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCompanionPanel.java`
- [ ] Verify no remaining references to AtCompanionPanel in the project:
  `grep -r "AtCompanionPanel" serial-debug-ui/src/`
- [ ] Verify compilation: `mvn clean compile -pl serial-debug-ui -am`
- [ ] Commit: `git add -A && git commit -m "chore(ui): delete AtCompanionPanel, replaced by inline sidebar"`

---

## Task 4: Final Verification

- [ ] Run: `mvn clean compile -pl serial-debug-ui -am`
- [ ] Verify: `test ! -f serial-debug-ui/src/main/java/io/github/serialdebug/ui/at/AtCompanionPanel.java && echo "deleted OK"`
- [ ] Verify: `grep -c "atPanel\|AtCompanionPanel\|onResponseReceived\|AT 伴侣" serial-debug-ui/src/main/java/io/github/serialdebug/ui/session/SessionTabContent.java` — all zero