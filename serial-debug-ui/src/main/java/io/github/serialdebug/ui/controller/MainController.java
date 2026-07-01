package io.github.serialdebug.ui.controller;

import io.github.serialdebug.core.parser.AsciiParser;
import io.github.serialdebug.core.parser.DataParser;
import io.github.serialdebug.core.parser.HexParser;
import io.github.serialdebug.core.util.RateCalculator;
import io.github.serialdebug.core.serial.SerialService;
import io.github.serialdebug.core.serial.JSerialCommService;
import io.github.serialdebug.core.log.LogService;
import io.github.serialdebug.core.log.FileLogService;
import io.github.serialdebug.ui.preset.JsonPresetService;
import io.github.serialdebug.ui.preset.PresetService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Main FXML controller — delegates all domain logic to sub-controllers.
 */
public class MainController implements Initializable {

    // ── FXML-injected controls (kept here for FXML binding) ───────────────

    @FXML private ComboBox<io.github.serialdebug.core.serial.SerialPortInfo> portCombo;
    @FXML private ComboBox<Integer> baudRateCombo;
    @FXML private ComboBox<Integer> dataBitsCombo;
    @FXML private ComboBox<Integer> stopBitsCombo;
    @FXML private ComboBox<io.github.serialdebug.core.serial.SerialConfig.Parity> parityCombo;
    @FXML private Button openCloseButton;
    @FXML private Button refreshButton;
    @FXML private Label statusLabel;
    @FXML private Label connectionStatusLabel;

    @FXML private Label rxCountLabel;
    @FXML private Label txCountLabel;
    @FXML private Label rxRateLabel;
    @FXML private Label txRateLabel;
    @FXML private TextArea hexViewArea;
    @FXML private TextArea asciiViewArea;
    @FXML private Button pauseScrollButton;
    @FXML private TextField searchField;
    @FXML private ToggleButton filterModeToggle;
    @FXML private ToggleButton caseSensitiveToggle;

    @FXML private ToggleButton hexModeToggle;
    @FXML private TextField sendTextField;
    @FXML private ComboBox<String> lineEndingCombo;
    @FXML private Button sendButton;
    @FXML private TextField intervalField;
    @FXML private TextField repeatCountField;
    @FXML private Button timedSendButton;
    @FXML private ListView<io.github.serialdebug.ui.preset.Preset> presetListView;
    @FXML private Button editPresetsButton;

    @FXML private Button startLoggingButton;
    @FXML private Button stopLoggingButton;
    @FXML private ToggleButton logHexToggle;
    @FXML private ToggleButton logAsciiToggle;
    @FXML private Label loggingStatusLabel;

    @FXML private Label statusRxLabel;
    @FXML private Label statusTxLabel;
    @FXML private Label clockLabel;

    @FXML private Button fileSendButton;
    @FXML private Label fileSendProgress;
    @FXML private Button cancelFileSendButton;

    // ── Sub-controllers ──────────────────────────────────────────────────

    private ToolbarController toolbarController;
    private SendController sendController;
    private FileSendController fileSendController;
    private DisplayController displayController;
    private LogController logController;
    private StatusBarController statusBarController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Shared services
        SerialService serialService = new JSerialCommService();
        DataParser hexParser = new HexParser();
        DataParser asciiParser = new AsciiParser();
        LogService logService = new FileLogService();
        PresetService presetService = new JsonPresetService();
        RateCalculator rxRateCalc = new RateCalculator();
        RateCalculator txRateCalc = new RateCalculator();

        // Serial listener → DisplayController
        serialService.setDataListener(data -> displayController().onDataReceived(data));

        // Create display controller first (needed by serial listener)
        displayController = new DisplayController(
                hexViewArea, asciiViewArea, pauseScrollButton,
                searchField, filterModeToggle, caseSensitiveToggle,
                rxCountLabel, txCountLabel, statusRxLabel, statusTxLabel,
                hexParser, asciiParser, logService, serialService,
                rxRateCalc, txRateCalc);
        displayController.initialize();

        // Create toolbar controller
        toolbarController = new ToolbarController(
                portCombo, baudRateCombo, dataBitsCombo, stopBitsCombo, parityCombo,
                openCloseButton, refreshButton, statusLabel, connectionStatusLabel,
                serialService);
        toolbarController.initialize();

        // Create send controller
        sendController = new SendController(
                sendTextField, hexModeToggle, lineEndingCombo, sendButton,
                intervalField, repeatCountField, timedSendButton,
                hexViewArea, asciiViewArea, presetListView, editPresetsButton,
                serialService, hexParser, asciiParser, logService, txRateCalc,
                presetService);
        sendController.initialize();

        // Create file send controller (needs stage from any node)
        Stage stage = (Stage) sendButton.getScene().getWindow();
        fileSendController = new FileSendController(
                fileSendButton, fileSendProgress, cancelFileSendButton,
                stage, sendController, displayController::updateStats);
        fileSendController.setPortOpen(false);
        sendController.setPortOpen(false);

        // Create log controller
        logController = new LogController(
                startLoggingButton, stopLoggingButton, logHexToggle, logAsciiToggle,
                loggingStatusLabel, stage, logService, null);
        logController.initialize();

        // Create status bar controller
        statusBarController = new StatusBarController(
                connectionStatusLabel, rxRateLabel, txRateLabel, clockLabel,
                rxRateCalc, txRateCalc);
        statusBarController.initialize();

        // Wire cross-controller callbacks
        toolbarController.setOnPortStateChange((connected, config) -> {
            sendController.setPortOpen(connected);
            fileSendController.setPortOpen(connected);
            statusBarController.updateConnectionStatus(connected, config);
            if (!connected) {
                displayController.resetRateCalcs();
                statusBarController.resetRateLabels();
            }
        });

        // Shutdown executors on window close
        Platform.runLater(() -> {
            if (stage != null) {
                stage.setOnCloseRequest(e -> {
                    sendController.shutdown();
                    fileSendController.shutdown();
                    statusBarController.shutdown();
                });
            }
        });
    }

    // ── @FXML handler delegators ─────────────────────────────────────────

    @FXML private void onOpenClose() { toolbarController.onOpenClose(); }
    @FXML private void onRefreshPorts() { toolbarController.refreshPortList(); }

    @FXML private void onSend() { sendController.onSend(); }
    @FXML private void onTimedSend() { sendController.onTimedSend(); }
    @FXML private void onClear() { displayController.onClear(); }
    @FXML private void onPauseScroll() { displayController.onPauseScroll(); }
    @FXML private void onEditPresets() { sendController.onEditPresets(); }
    @FXML private void onFileSend() { fileSendController.onFileSend(); }
    @FXML private void onCancelFileSend() { fileSendController.onCancelFileSend(); }

    @FXML private void onStartLogging() { logController.onStartLogging(); }
    @FXML private void onStopLogging() { logController.onStopLogging(); }

    // ── Accessors for sub-controllers (used by initialize wiring) ────────

    private DisplayController displayController() {
        return displayController;
    }
}
