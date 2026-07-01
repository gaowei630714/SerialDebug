package io.github.serialdebug.ui.controller;

import io.github.serialdebug.core.parser.AsciiParser;
import io.github.serialdebug.core.parser.DataParser;
import io.github.serialdebug.core.parser.HexParser;
import io.github.serialdebug.core.log.Direction;
import io.github.serialdebug.core.log.LogService;
import io.github.serialdebug.core.serial.SerialService;
import io.github.serialdebug.core.util.RateCalculator;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import io.github.serialdebug.ui.preset.Preset;
import io.github.serialdebug.ui.preset.PresetService;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Handles manual send, timed send, line-ending, presets, and text parsing.
 */
public class SendController {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final TextField sendTextField;
    private final ToggleButton hexModeToggle;
    private final ComboBox<String> lineEndingCombo;
    private final Button sendButton;
    private final TextField intervalField;
    private final TextField repeatCountField;
    private final Button timedSendButton;
    private final TextArea hexViewArea;
    private final TextArea asciiViewArea;
    private final ListView<Preset> presetListView;
    private final Button editPresetsButton;

    private final SerialService serialService;
    private final DataParser hexParser;
    private final DataParser asciiParser;
    private final LogService logService;
    private final RateCalculator txRateCalc;
    private final PresetService presetService;
    private final ObservableList<Preset> presets = FXCollections.observableArrayList();

    private ScheduledExecutorService timerExecutor;
    private ScheduledFuture<?> timedSendFuture;
    private final AtomicInteger timedSendRemaining = new AtomicInteger(0);

    private Consumer<String> onPresetSelected;

    public SendController(
            TextField sendTextField,
            ToggleButton hexModeToggle,
            ComboBox<String> lineEndingCombo,
            Button sendButton,
            TextField intervalField,
            TextField repeatCountField,
            Button timedSendButton,
            TextArea hexViewArea,
            TextArea asciiViewArea,
            ListView<Preset> presetListView,
            Button editPresetsButton,
            SerialService serialService,
            DataParser hexParser,
            DataParser asciiParser,
            LogService logService,
            RateCalculator txRateCalc,
            PresetService presetService) {
        this.sendTextField = sendTextField;
        this.hexModeToggle = hexModeToggle;
        this.lineEndingCombo = lineEndingCombo;
        this.sendButton = sendButton;
        this.intervalField = intervalField;
        this.repeatCountField = repeatCountField;
        this.timedSendButton = timedSendButton;
        this.hexViewArea = hexViewArea;
        this.asciiViewArea = asciiViewArea;
        this.presetListView = presetListView;
        this.editPresetsButton = editPresetsButton;
        this.serialService = serialService;
        this.hexParser = hexParser;
        this.asciiParser = asciiParser;
        this.logService = logService;
        this.txRateCalc = txRateCalc;
        this.presetService = presetService;
    }

    public void setOnPresetSelected(Consumer<String> callback) {
        this.onPresetSelected = callback;
    }

    public void initialize() {
        // Line-ending combo
        lineEndingCombo.getItems().setAll("None", "CR (\\r)", "LF (\\n)", "CRLF (\\r\\n)");
        lineEndingCombo.getSelectionModel().select(3); // default CRLF

        // Digit-only filter for interval/count fields
        intervalField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) intervalField.setText(val.replaceAll("\\D", ""));
        });
        repeatCountField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) repeatCountField.setText(val.replaceAll("\\D", ""));
        });

        // Disable send button when port is closed
        sendButton.setDisable(true);

        // Presets
        presets.setAll(presetService.load());
        setupPresetListView();
    }

    public void setPortOpen(boolean open) {
        sendButton.setDisable(!open);
    }

    public void shutdown() {
        if (timerExecutor != null) timerExecutor.shutdownNow();
    }

    // ── Send ──────────────────────────────────────────────────────────────

    public void onSend() {
        byte[] data = prepareSendData(sendTextField.getText());
        if (data != null) {
            doSend(data);
        }
    }

    /**
     * Parse send-text into bytes using current HEX/ASCII mode + line ending.
     * Called from FX thread.
     */
    public byte[] prepareSendData(String text) {
        return prepareSendData(text, hexModeToggle.isSelected(), lineEndingCombo.getValue());
    }

    /**
     * Parse send-text into bytes using explicit HEX/ASCII mode + line ending.
     * Thread-safe — reads no JavaFX properties. Can be called from background threads.
     */
    public byte[] prepareSendData(String text, boolean hexMode, String lineEnding) {
        if (text == null || text.isEmpty()) return null;
        byte[] data;
        if (hexMode) {
            try {
                data = hexParser.encode(text);
            } catch (IllegalArgumentException e) {
                UiHelper.showError("Invalid HEX input", e);
                return null;
            }
        } else {
            data = asciiParser.encode(text);
            if (lineEnding != null) {
                String suffix = switch (lineEnding) {
                    case "CR (\\r)" -> "\r";
                    case "LF (\\n)" -> "\n";
                    case "CRLF (\\r\\n)" -> "\r\n";
                    default -> "";
                };
                if (!suffix.isEmpty()) {
                    byte[] suffixBytes = suffix.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    byte[] withNewline = new byte[data.length + suffixBytes.length];
                    System.arraycopy(data, 0, withNewline, 0, data.length);
                    System.arraycopy(suffixBytes, 0, withNewline, data.length, suffixBytes.length);
                    data = withNewline;
                }
            }
        }
        return data;
    }

    public void doSend(byte[] data) {
        try {
            serialService.sendData(data);
            txRateCalc.addSample(data.length);
            String timestamp = LocalTime.now().format(TIME_FORMATTER);
            String hexStr = hexParser.decode(data, 0, data.length);
            String asciiStr = asciiParser.decode(data, 0, data.length);

            if (logService.isLogging()) {
                logService.log(data, 0, data.length, Direction.TX);
            }

            hexViewArea.appendText("[" + timestamp + " TX] " + hexStr + "\n");
            asciiViewArea.appendText("[" + timestamp + " TX] " + asciiStr + "\n");
            hexViewArea.setScrollTop(Double.MAX_VALUE);
            asciiViewArea.setScrollTop(Double.MAX_VALUE);
        } catch (IOException e) {
            UiHelper.showError("Failed to send data", e);
        }
    }

    // ── Timed Send ────────────────────────────────────────────────────────

    public void onTimedSend() {
        if (timedSendFuture != null && !timedSendFuture.isDone()) {
            // Stop running timer
            timedSendFuture.cancel(false);
            timedSendFuture = null;
            timedSendButton.setText("");
            timedSendButton.setGraphic(new FontIcon("mdi2t-timer"));
            return;
        }

        String text = sendTextField.getText();
        if (text == null || text.isEmpty()) return;

        int intervalMs;
        try {
            intervalMs = Integer.parseInt(intervalField.getText());
            if (intervalMs < 10 || intervalMs > 60000) {
                UiHelper.showWarning("Interval must be 10-60000 ms");
                return;
            }
        } catch (NumberFormatException e) {
            UiHelper.showWarning("Invalid interval");
            return;
        }

        int count = 0;
        try {
            count = Integer.parseInt(repeatCountField.getText());
        } catch (NumberFormatException ignored) {}
        final int finalCount = count;

        if (timerExecutor == null || timerExecutor.isShutdown()) {
            timerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "timed-send");
                t.setDaemon(true);
                return t;
            });
        }

        timedSendRemaining.set(count);
        timedSendButton.setGraphic(new FontIcon("mdi2s-stop"));

        timedSendFuture = timerExecutor.scheduleAtFixedRate(() -> {
            if (finalCount > 0 && timedSendRemaining.get() <= 0) {
                javafx.application.Platform.runLater(() -> {
                    timedSendFuture.cancel(false);
                    timedSendFuture = null;
                    timedSendButton.setText("");
                    timedSendButton.setGraphic(new FontIcon("mdi2t-timer"));
                });
                return;
            }
            if (timedSendRemaining.get() > 0) timedSendRemaining.decrementAndGet();
            final boolean hexMode = hexModeToggle.isSelected();
            final String lineEnding = lineEndingCombo.getValue();
            javafx.application.Platform.runLater(() -> {
                String currentText = sendTextField.getText();
                if (currentText == null || currentText.isEmpty()) return;
                byte[] data = prepareSendData(currentText, hexMode, lineEnding);
                if (data != null) {
                    doSend(data);
                }
            });
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    // ── Presets ───────────────────────────────────────────────────────────

    private void setupPresetListView() {
        presetListView.setItems(presets);
        presetListView.setCellFactory(lv -> new ListCell<>() {
            private final VBox content = new VBox(2);
            private final Label nameLabel = new Label();
            private final Label dataLabel = new Label();

            {
                nameLabel.getStyleClass().add("preset-name");
                dataLabel.getStyleClass().add("preset-data");
                dataLabel.setWrapText(false);
                dataLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                content.getChildren().addAll(nameLabel, dataLabel);
            }

            @Override
            protected void updateItem(Preset item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    setTooltip(null);
                    setOnMouseClicked(null);
                } else {
                    nameLabel.setText(item.getName());
                    dataLabel.setText(item.getData());
                    setGraphic(content);
                    setTooltip(new Tooltip(item.getData()));
                    setOnMouseClicked(e -> {
                        sendTextField.setText(item.getData());
                        if (onPresetSelected != null) {
                            onPresetSelected.accept(item.getData());
                        }
                    });
                }
            }
        });
    }

    public void onEditPresets() {
        openEditDialog();
    }

    private void openEditDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("编辑指令预设");
        dialog.initOwner(editPresetsButton.getScene().getWindow());

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TableView<Preset> table = new TableView<>();
        table.setItems(presets);
        table.setEditable(true);
        table.setPrefHeight(300);
        table.setPrefWidth(480);

        TableColumn<Preset, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameCol.setCellFactory(col -> new PresetTextFieldCell());
        nameCol.setOnEditCommit(e -> e.getRowValue().setName(e.getNewValue()));
        nameCol.setPrefWidth(160);

        TableColumn<Preset, String> dataCol = new TableColumn<>("数据");
        dataCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getData()));
        dataCol.setCellFactory(col -> new PresetTextFieldCell());
        dataCol.setOnEditCommit(e -> e.getRowValue().setData(e.getNewValue()));
        dataCol.setPrefWidth(260);

        TableColumn<Preset, Void> actionCol = new TableColumn<>("操作");
        actionCol.setPrefWidth(60);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button delBtn = new Button("删除");
            {
                delBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #e74c3c; -fx-cursor: hand;");
                delBtn.setOnAction(e -> {
                    Preset p = getTableRow().getItem();
                    if (p != null) presets.remove(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : delBtn);
            }
        });

        table.getColumns().addAll(nameCol, dataCol, actionCol);

        Button addRowBtn = new Button("+ 新增");
        addRowBtn.setOnAction(e -> presets.add(new Preset("新预设", "")));

        VBox content = new VBox(8, table, addRowBtn);
        content.setPadding(new javafx.geometry.Insets(8, 0, 0, 0));

        dialogPane.setContent(content);
        dialogPane.setPrefSize(520, 420);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                presets.removeIf(p -> (p.getName() == null || p.getName().isBlank())
                        && (p.getData() == null || p.getData().isBlank()));
                presetService.save(presets);
            }
        });
    }

    private static class PresetTextFieldCell extends TableCell<Preset, String> {
        private final TextField textField = new TextField();

        PresetTextFieldCell() {
            textField.setOnAction(e -> commitEdit(textField.getText()));
            textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal && isEditing()) {
                    commitEdit(textField.getText());
                }
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (isEmpty()) return;
            textField.setText(getItem());
            setGraphic(textField);
            setText(null);
            textField.requestFocus();
            textField.selectAll();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else if (isEditing()) {
                if (textField != null) textField.setText(item);
                setGraphic(textField);
                setText(null);
            } else {
                setText(item);
                setGraphic(null);
            }
        }
    }
}
