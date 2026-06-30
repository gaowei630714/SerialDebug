package io.github.serialdebug.ui.controller;

import io.github.serialdebug.core.parser.AsciiParser;
import io.github.serialdebug.core.parser.DataParser;
import io.github.serialdebug.core.parser.HexParser;
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
import io.github.serialdebug.core.log.Direction;
import io.github.serialdebug.core.log.FileLogService;
import io.github.serialdebug.core.log.LogFormat;
import io.github.serialdebug.core.log.LogService;
import io.github.serialdebug.ui.preset.JsonPresetService;
import io.github.serialdebug.ui.preset.Preset;
import io.github.serialdebug.ui.preset.PresetService;
import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

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
    @FXML private TextArea hexViewArea;
    @FXML private TextArea asciiViewArea;
    @FXML private ToggleButton hexModeToggle;
    @FXML private TextField sendTextField;
    @FXML private CheckBox appendNewlineCheckBox;
    @FXML private Button sendButton;
    @FXML private Button clearButton;
    @FXML private Button startLoggingButton;
    @FXML private Button stopLoggingButton;
    @FXML private ToggleButton logHexToggle;
    @FXML private ToggleButton logAsciiToggle;
    @FXML private Label connectionStatusLabel;
    @FXML private Label loggingStatusLabel;
    @FXML private Label statusRxLabel;
    @FXML private Label statusTxLabel;
    @FXML private ListView<Preset> presetListView;
    @FXML private Button editPresetsButton;

    private final SerialService serialService = new JSerialCommService();
    private final DataParser hexParser = new HexParser();
    private final DataParser asciiParser = new AsciiParser();
    private final AtomicBoolean isOpen = new AtomicBoolean(false);
    private final LogService logService = new FileLogService();
    private final PresetService presetService = new JsonPresetService();
    private final ObservableList<Preset> presets = FXCollections.observableArrayList();

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

        // Load presets and bind to the left-panel list view
        presets.setAll(presetService.load());
        setupPresetListView();
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
        SerialConfig config = new SerialConfig();
        config.setPortName(selectedPort.getSystemPortName());
        config.setBaudRate(baudRateCombo.getValue());
        config.setDataBits(dataBitsCombo.getValue());
        config.setStopBits(stopBitsCombo.getValue());
        config.setParity(parityCombo.getValue());
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
            appendStatus("Disconnected");
        }
    }

    private void updatePortState(boolean connected) {
        if (connected) {
            openCloseButton.setText("Close");
            openCloseButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
            connectionStatusLabel.setText("Connected: " + serialService.getCurrentConfig());
        } else {
            openCloseButton.setText("Open");
            openCloseButton.setStyle("");
            connectionStatusLabel.setText("Disconnected");
        }
    }

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
            final byte[] logData = data;
            Platform.runLater(() -> {
                hexViewArea.appendText("[" + timestamp + " TX] " + hexStr + "\n");
                asciiViewArea.appendText("[" + timestamp + " TX] " + asciiStr + "\n");
                hexViewArea.setScrollTop(Double.MAX_VALUE);
                asciiViewArea.setScrollTop(Double.MAX_VALUE);
                if (logService.isLogging()) {
                    logService.log(logData, 0, logData.length, Direction.TX);
                }
            });
            updateStats();
        } catch (IOException e) {
            showError("Failed to send data", e);
        }
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
        Platform.runLater(() -> {
            if (hexViewArea.getLength() > 1_000_000) hexViewArea.clear();
            if (asciiViewArea.getLength() > 1_000_000) asciiViewArea.clear();
            hexViewArea.appendText("[" + timestamp + " RX] " + hexStr + "\n");
            asciiViewArea.appendText("[" + timestamp + " RX] " + asciiStr + "\n");
            hexViewArea.setScrollTop(Double.MAX_VALUE);
            asciiViewArea.setScrollTop(Double.MAX_VALUE);
            updateStats();
            if (logService.isLogging()) {
                logService.log(data, 0, data.length, Direction.RX);
            }
        });
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
}
