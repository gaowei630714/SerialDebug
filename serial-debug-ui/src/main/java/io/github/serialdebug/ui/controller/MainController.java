package io.github.serialdebug.ui.controller;

import io.github.serialdebug.core.parser.AsciiParser;
import io.github.serialdebug.core.parser.DataParser;
import io.github.serialdebug.core.parser.HexParser;
import io.github.serialdebug.core.util.RateCalculator;
import io.github.serialdebug.core.serial.JSerialCommService;
import io.github.serialdebug.core.serial.SerialConfig;
import io.github.serialdebug.core.serial.SerialPortInfo;
import io.github.serialdebug.core.serial.SerialService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import io.github.serialdebug.core.log.Direction;
import io.github.serialdebug.core.log.FileLogService;
import io.github.serialdebug.core.log.LogFormat;
import io.github.serialdebug.core.log.LogService;
import io.github.serialdebug.ui.preset.JsonPresetService;
import io.github.serialdebug.ui.preset.Preset;
import io.github.serialdebug.ui.preset.PresetService;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainController implements Initializable {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @FXML private ComboBox<SerialPortInfo> portCombo;
    @FXML private ComboBox<Integer> baudRateCombo;
    @FXML private ComboBox<Integer> dataBitsCombo;
    @FXML private ComboBox<Integer> stopBitsCombo;
    @FXML private ComboBox<SerialConfig.Parity> parityCombo;
    @FXML private Button openCloseButton;
    @FXML private Label statusLabel;
    @FXML private Label rxCountLabel;
    @FXML private Label txCountLabel;
    @FXML private Label rxRateLabel;
    @FXML private Label txRateLabel;
    @FXML private TextArea hexViewArea;
    @FXML private TextArea asciiViewArea;
    @FXML private ToggleButton hexModeToggle;
    @FXML private TextField sendTextField;
    @FXML private ComboBox<String> lineEndingCombo;
    @FXML private Button sendButton;
    @FXML private TextField intervalField;
    @FXML private TextField repeatCountField;
    @FXML private Button timedSendButton;
    @FXML private TextField searchField;
    @FXML private ToggleButton filterModeToggle;
    @FXML private ToggleButton caseSensitiveToggle;
    @FXML private Button clearButton;
    @FXML private Button pauseScrollButton;
    @FXML private Button startLoggingButton;
    @FXML private Button stopLoggingButton;
    @FXML private ToggleButton logHexToggle;
    @FXML private ToggleButton logAsciiToggle;
    @FXML private Label connectionStatusLabel;
    @FXML private Label loggingStatusLabel;
    @FXML private Label statusRxLabel;
    @FXML private Label statusTxLabel;
    @FXML private Label clockLabel;
    @FXML private ListView<Preset> presetListView;
    @FXML private Button editPresetsButton;
    @FXML private Button fileSendButton;
    @FXML private Label fileSendProgress;
    @FXML private Button cancelFileSendButton;

    private volatile boolean autoScrollPaused = false;
    private final List<String> hexLineBuffer = new ArrayList<>();
    private final List<String> asciiLineBuffer = new ArrayList<>();
    private final Object bufferLock = new Object();
    private final SerialService serialService = new JSerialCommService();
    private final DataParser hexParser = new HexParser();
    private final DataParser asciiParser = new AsciiParser();
    private final AtomicBoolean isOpen = new AtomicBoolean(false);
    private final LogService logService = new FileLogService();
    private final PresetService presetService = new JsonPresetService();
    private final ObservableList<Preset> presets = FXCollections.observableArrayList();
    private final RateCalculator rxRateCalc = new RateCalculator();
    private final RateCalculator txRateCalc = new RateCalculator();

    private record BatchEntry(String timestamp, String hex, String ascii, Direction dir) {}

    private final List<BatchEntry> batchBuffer = new ArrayList<>();
    private final AtomicBoolean batchPending = new AtomicBoolean(false);

    private ScheduledExecutorService timerExecutor;
    private ScheduledFuture<?> timedSendFuture;
    private final AtomicInteger timedSendRemaining = new AtomicInteger(0);

    private volatile boolean fileSendCancelled;
    private java.util.concurrent.ExecutorService fileSendExecutor;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        baudRateCombo.getItems().setAll(
                300, 600, 1200, 2400, 4800, 9600, 19200, 38400,
                57600, 115200, 230400, 460800, 921600);
        baudRateCombo.getSelectionModel().select(Integer.valueOf(115200));
        dataBitsCombo.getItems().setAll(5, 6, 7, 8);
        dataBitsCombo.getSelectionModel().select(Integer.valueOf(8));
        stopBitsCombo.getItems().setAll(1, 2);
        stopBitsCombo.getSelectionModel().select(Integer.valueOf(1));
        parityCombo.getItems().setAll(SerialConfig.Parity.values());
        parityCombo.getSelectionModel().select(SerialConfig.Parity.NONE);
        lineEndingCombo.getItems().setAll("None", "CR (\\r)", "LF (\\n)", "CRLF (\\r\\n)");
        lineEndingCombo.getSelectionModel().select(3); // default CRLF
        serialService.setDataListener(this::onDataReceived);
        refreshPortList();
        sendButton.disableProperty().bind(
                javafx.beans.binding.Bindings.not(openCloseButton.disabledProperty()));

        // Logging format toggles: mutually exclusive
        ToggleButton hexToggle = logHexToggle;
        ToggleButton asciiToggle = logAsciiToggle;
        hexToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) asciiToggle.setSelected(false);
        });
        asciiToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) hexToggle.setSelected(false);
        });

        // Only allow digits in interval/count fields
        intervalField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) intervalField.setText(val.replaceAll("\\D", ""));
        });
        repeatCountField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("\\d*")) repeatCountField.setText(val.replaceAll("\\D", ""));
        });

        // Search/filter listeners
        searchField.textProperty().addListener((obs, old, val) -> applyFilter());
        filterModeToggle.selectedProperty().addListener((obs, old, val) -> applyFilter());
        caseSensitiveToggle.selectedProperty().addListener((obs, old, val) -> {
            if (filterModeToggle.isSelected()) applyFilter();
        });

        // Load presets and bind to the left-panel list view
        presets.setAll(presetService.load());
        setupPresetListView();

        // Shut down executors when the window closes to avoid leaking thread pools
        Platform.runLater(() -> {
            Stage stage = (Stage) timedSendButton.getScene().getWindow();
            stage.setOnCloseRequest(e -> {
                if (timerExecutor != null) timerExecutor.shutdownNow();
                if (fileSendExecutor != null) fileSendExecutor.shutdownNow();
            });
        });

        // Update rate labels + clock every 1 second
        var clockFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        clockLabel.setText(LocalTime.now().format(clockFormatter));
        var rateTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> updateRateLabels()),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1),
                        e -> clockLabel.setText(LocalTime.now().format(clockFormatter)))
        );
        rateTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        rateTimer.play();
    }

    private void updateRateLabels() {
        double rxRate = rxRateCalc.getRate();
        double txRate = txRateCalc.getRate();
        rxRateLabel.setText(String.format("RX rate: %.1f B/s", rxRate));
        txRateLabel.setText(String.format("TX rate: %.1f B/s", txRate));
    }

    @FXML
    private void onRefreshPorts() {
        refreshPortList();
    }

    private void refreshPortList() {
        try {
            List<SerialPortInfo> ports = serialService.listPorts();
            portCombo.getItems().setAll(ports);
            if (!ports.isEmpty()) {
                portCombo.getSelectionModel().select(0);
            }
        } catch (Exception e) {
            showError("Failed to list ports", e);
        }
    }

    @FXML
    private void onClear() {
        hexViewArea.clear();
        asciiViewArea.clear();
    }

    @FXML
    private void onPauseScroll() {
        autoScrollPaused = !autoScrollPaused;
        if (autoScrollPaused) {
            pauseScrollButton.setText("Resume");
            pauseScrollButton.getStyleClass().add("btn-warning");
        } else {
            pauseScrollButton.setText("Pause");
            pauseScrollButton.getStyleClass().remove("btn-warning");
            // Scroll to bottom on resume
            hexViewArea.setScrollTop(Double.MAX_VALUE);
            asciiViewArea.setScrollTop(Double.MAX_VALUE);
        }
    }

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

    @FXML
    private void onOpenClose() {
        if (isOpen.get()) {
            closePort();
        } else {
            openPort();
        }
    }

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

    private void closePort() {
        try {
            serialService.close();
        } finally {
            isOpen.set(false);
            updatePortState(false);
            rxRateCalc.reset();
            txRateCalc.reset();
            rxRateLabel.setText("RX rate: 0 B/s");
            txRateLabel.setText("TX rate: 0 B/s");
            appendStatus("Disconnected");
        }
    }

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

    @FXML
    private void onSend() {
        byte[] data = prepareSendData(sendTextField.getText());
        if (data != null) {
            doSend(data);
        }
    }

    /**
     * Parse send-text into bytes using current HEX/ASCII mode + line ending.
     * Returns null on parse error (warning already shown). Called from FX thread.
     */
    private byte[] prepareSendData(String text) {
        return prepareSendData(text, hexModeToggle.isSelected(), lineEndingCombo.getValue());
    }

    /**
     * Parse send-text into bytes using explicit HEX/ASCII mode + line ending.
     * Thread-safe — reads no JavaFX properties. Can be called from background threads.
     */
    private byte[] prepareSendData(String text, boolean hexMode, String lineEnding) {
        if (text == null || text.isEmpty()) return null;
        byte[] data;
        if (hexMode) {
            try {
                data = hexParser.encode(text);
            } catch (IllegalArgumentException e) {
                showError("Invalid HEX input", e);
                return null;
            }
        } else {
            data = asciiParser.encode(text);
            // Apply line ending
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

    /**
     * Send pre-encoded bytes, update display + log. Called from both onSend() and timed send.
     */
    private void doSend(byte[] data) {
        try {
            serialService.sendData(data);
            txRateCalc.addSample(data.length);
            String timestamp = LocalTime.now().format(TIME_FORMATTER);
            String hexStr = hexParser.decode(data, 0, data.length);
            String asciiStr = asciiParser.decode(data, 0, data.length);

            if (logService.isLogging()) {
                logService.log(data, 0, data.length, Direction.TX);
            }

            final String ts = timestamp;
            final String hexDisplay = hexStr;
            final String asciiDisplay = asciiStr;
            Platform.runLater(() -> {
                hexViewArea.appendText("[" + ts + " TX] " + hexDisplay + "\n");
                asciiViewArea.appendText("[" + ts + " TX] " + asciiDisplay + "\n");
                if (!autoScrollPaused) {
                    hexViewArea.setScrollTop(Double.MAX_VALUE);
                    asciiViewArea.setScrollTop(Double.MAX_VALUE);
                }
            });
            updateStats();
        } catch (IOException e) {
            showError("Failed to send data", e);
        }
    }

    @FXML
    private void onTimedSend() {
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
                showWarning("Interval must be 10-60000 ms");
                return;
            }
        } catch (NumberFormatException e) {
            showWarning("Invalid interval");
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
            // finalCount == 0 means infinite — only stop when user clicks stop
            if (finalCount > 0 && timedSendRemaining.get() <= 0) {
                // Reached limit — stop
                Platform.runLater(() -> {
                    timedSendFuture.cancel(false);
                    timedSendFuture = null;
                    timedSendButton.setText("");
                    timedSendButton.setGraphic(new FontIcon("mdi2t-timer"));
                });
                return;
            }
            if (timedSendRemaining.get() > 0) timedSendRemaining.decrementAndGet();
            Platform.runLater(() -> {
                // Re-use the send logic without triggering another timed send
                String currentText = sendTextField.getText();
                if (currentText == null || currentText.isEmpty()) return;
                byte[] data = prepareSendData(currentText);
                if (data != null) {
                    doSend(data);
                }
            });
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    // ---------------------------------------------------------------
    // Command presets
    // ---------------------------------------------------------------

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
                    setOnMouseClicked(e -> sendTextField.setText(item.getData()));
                }
            }
        });
    }

    @FXML
    private void onEditPresets() {
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
        content.setPadding(new Insets(8, 0, 0, 0));

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

    /**
     * A TextFieldTableCell variant that commits its value when focus is lost,
     * preventing data loss when the user edits a cell and clicks OK without
     * pressing Enter.
     */
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

    private void onDataReceived(byte[] data) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String hexStr = hexParser.decode(data, 0, data.length);
        String asciiStr = asciiParser.decode(data, 0, data.length);

        rxRateCalc.addSample(data.length);

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

    private void flushBatch() {
        List<BatchEntry> entries;
        synchronized (batchBuffer) {
            entries = new ArrayList<>(batchBuffer);
            batchBuffer.clear();
        }
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
        }
        // Always buffer for search/filter
        synchronized (bufferLock) {
            for (BatchEntry e : entries) {
                String hexLine = "[" + e.timestamp + " " + e.dir + "] " + e.hex;
                String asciiLine = "[" + e.timestamp + " " + e.dir + "] " + e.ascii;
                hexLineBuffer.add(hexLine);
                asciiLineBuffer.add(asciiLine);
            }
        }
        if (!autoScrollPaused) {
            hexViewArea.setScrollTop(Double.MAX_VALUE);
            asciiViewArea.setScrollTop(Double.MAX_VALUE);
        }
        updateStats();
        batchPending.set(false);
        // Re-arm if new data arrived during flush (race-safe)
        synchronized (batchBuffer) {
            if (!batchBuffer.isEmpty() && batchPending.compareAndSet(false, true)) {
                Platform.runLater(this::flushBatch);
            }
        }
    }

    private void applyFilter() {
        // Called from FX listeners — already on FX thread, no Platform.runLater needed
        String query = searchField.getText();
        StringBuilder filteredHex = new StringBuilder();
        StringBuilder filteredAscii = new StringBuilder();
        synchronized (bufferLock) {
            for (int i = 0; i < hexLineBuffer.size(); i++) {
                String hexLine = hexLineBuffer.get(i);
                String asciiLine = asciiLineBuffer.get(i);
                if (!filterModeToggle.isSelected() || query == null || query.isEmpty()) {
                    // Filter off — include all lines
                    filteredHex.append(hexLine).append('\n');
                    filteredAscii.append(asciiLine).append('\n');
                } else {
                    boolean matches;
                    if (caseSensitiveToggle.isSelected()) {
                        matches = hexLine.contains(query) || asciiLine.contains(query);
                    } else {
                        matches = hexLine.toLowerCase().contains(query.toLowerCase())
                                || asciiLine.toLowerCase().contains(query.toLowerCase());
                    }
                    if (matches) {
                        filteredHex.append(hexLine).append('\n');
                        filteredAscii.append(asciiLine).append('\n');
                    }
                }
            }
        }
        hexViewArea.setText(filteredHex.toString());
        asciiViewArea.setText(filteredAscii.toString());
        if (!autoScrollPaused) {
            hexViewArea.setScrollTop(Double.MAX_VALUE);
            asciiViewArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    private void updateStats() {
        Platform.runLater(() -> {
            long rx = serialService.getBytesReceived();
            long tx = serialService.getBytesSent();
            rxCountLabel.setText("RX: " + rx + " bytes");
            txCountLabel.setText("TX: " + tx + " bytes");
            statusRxLabel.setText("RX: " + rx);
            statusTxLabel.setText("TX: " + tx);
            updateLoggingStatus();
        });
    }

    private void updateLoggingStatus() {
        if (logService.isLogging()) {
            loggingStatusLabel.setText("Recording: " + logService.getCurrentFile().getFileName()
                    + " (" + (logService.getBytesLogged() / 1024) + " KB)");
        } else {
            loggingStatusLabel.setText("Not recording");
        }
    }

    private void appendStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String title, Exception e) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            alert.setTitle(title);
            alert.showAndWait();
        });
    }

    private void showWarning(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
            alert.setTitle("Warning");
            alert.showAndWait();
        });
    }

    // ---------------------------------------------------------------
    // File Send
    // ---------------------------------------------------------------

    @FXML
    private void onFileSend() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select file to send");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("Binary", "*.bin"),
                new FileChooser.ExtensionFilter("HEX", "*.hex"),
                new FileChooser.ExtensionFilter("Text", "*.txt")
        );
        Stage stage = (Stage) fileSendButton.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        if (file == null) return;

        // Mode based on extension
        boolean binaryMode = !file.getName().toLowerCase().endsWith(".txt");

        // Snapshot FX properties for background thread
        boolean hexMode = hexModeToggle.isSelected();
        String lineEnding = lineEndingCombo.getValue();

        if (fileSendExecutor == null || fileSendExecutor.isShutdown()) {
            fileSendExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "file-send");
                t.setDaemon(true);
                return t;
            });
        }

        fileSendCancelled = false;
        fileSendButton.setDisable(true);
        cancelFileSendButton.setDisable(false);
        cancelFileSendButton.setVisible(true);
        fileSendProgress.setText("Starting...");

        fileSendExecutor.submit(() -> {
            try {
                if (binaryMode) {
                    sendBinaryFile(file, hexMode, lineEnding);
                } else {
                    sendTextFile(file, hexMode, lineEnding);
                }
            } catch (IOException e) {
                Platform.runLater(() -> showError("File send failed", e));
            }
            Platform.runLater(() -> {
                fileSendButton.setDisable(false);
                cancelFileSendButton.setDisable(true);
                cancelFileSendButton.setVisible(false);
                if (!fileSendCancelled) {
                    fileSendProgress.setText("Complete");
                } else {
                    fileSendProgress.setText("Cancelled");
                }
            });
        });
    }

    @FXML
    private void onCancelFileSend() {
        fileSendCancelled = true;
    }

    private void sendTextFile(File file, boolean hexMode, String lineEnding) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        int total = lines.size();
        for (int i = 0; i < total; i++) {
            if (fileSendCancelled) return;
            String line = lines.get(i).strip();
            if (line.isEmpty()) continue;
            byte[] data = prepareSendData(line, hexMode, lineEnding);
            if (data != null) {
                doSend(data);
            }
            final int current = i + 1;
            Platform.runLater(() -> fileSendProgress.setText(current + "/" + total));
            try {
                Thread.sleep(50); // 50ms between lines
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void sendBinaryFile(File file, boolean hexMode, String lineEnding) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        int total = fileBytes.length;
        int chunkSize = 1024;
        int chunks = (total + chunkSize - 1) / chunkSize;

        for (int i = 0; i < chunks; i++) {
            if (fileSendCancelled) return;
            int offset = i * chunkSize;
            int length = Math.min(chunkSize, total - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(fileBytes, offset, chunk, 0, length);
            doSend(chunk);
            final int pct = (int) ((i + 1) * 100L / chunks);
            final int pkts = i + 1;
            Platform.runLater(() -> fileSendProgress.setText(pct + "% (" + pkts + "/" + chunks + " pkts)"));
            try {
                Thread.sleep(20); // 20ms between chunks
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
