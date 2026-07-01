package io.github.serialdebug.ui.session;

import io.github.serialdebug.core.log.FileLogService;
import io.github.serialdebug.core.log.LogService;
import io.github.serialdebug.core.serial.SerialService;
import io.github.serialdebug.core.parser.HexParser;
import io.github.serialdebug.core.parser.AsciiParser;
import io.github.serialdebug.core.util.RateCalculator;
import io.github.serialdebug.ui.controller.*;
import io.github.serialdebug.ui.preset.JsonPresetService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Builds the complete UI content for one serial session within a tab.
 * Owns per-session controllers: toolbar, send, file-send, display, log, status-bar.
 */
public class SessionTabContent extends BorderPane {

    private final SerialSession session;
    private ToolbarController toolbarController;
    private SendController sendController;
    private DisplayController displayController;
    private StatusBarController statusBarController;
    private FileSendController fileSendController;
    private LogController logController;

    private final SerialService serialService;
    private final LogService logService = new FileLogService();
    private final RateCalculator rxRateCalc = new RateCalculator();
    private final RateCalculator txRateCalc = new RateCalculator();

    // File send controls (created per-session inside send area)
    private final Button fileSendBtn = new Button("Send File", new FontIcon("mdi2f-file"));
    private final Label fileSendProgress = new Label("");
    private final Button cancelFileSendBtn = new Button("Cancel");
    private final Stage stage;

    public SessionTabContent(SerialSession session,
                             ToggleButton logHexToggle, ToggleButton logAsciiToggle,
                             Button startLoggingButton, Button stopLoggingButton,
                             Label loggingStatusLabel) {
        this.session = session;
        this.serialService = session.getSerialService();
        this.stage = null; // will get from a control's scene
        buildUI(logHexToggle, logAsciiToggle, startLoggingButton, stopLoggingButton, loggingStatusLabel);
    }

    private void buildUI(ToggleButton logHexToggle, ToggleButton logAsciiToggle,
                         Button startLoggingButton, Button stopLoggingButton,
                         Label loggingStatusLabel) {
        VBox root = new VBox(0);

        ToolBar portBar = createPortBar();
        root.getChildren().add(portBar);

        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.25);

        VBox leftPanel = createStatsPanel();
        splitPane.getItems().add(leftPanel);

        VBox rightPanel = createDisplayArea();
        splitPane.getItems().add(rightPanel);

        VBox.setVgrow(splitPane, Priority.ALWAYS);
        root.getChildren().add(splitPane);

        HBox statusBar = createStatusBar();
        root.getChildren().add(statusBar);

        setCenter(root);

        // ── File send event wiring (delegate to controller when ready) ──
        cancelFileSendBtn.setDisable(true);
        cancelFileSendBtn.setVisible(false);
        fileSendBtn.setOnAction(e -> {
            if (fileSendController != null) fileSendController.onFileSend();
        });
        cancelFileSendBtn.setOnAction(e -> {
            if (fileSendController != null) fileSendController.onCancelFileSend();
        });

        // ── Defer scene-dependent controller init ──
        Platform.runLater(() -> {
            Stage resolvedStage = (Stage) root.getScene().getWindow();

            fileSendController = new FileSendController(
                    fileSendBtn, fileSendProgress, cancelFileSendBtn,
                    resolvedStage, sendController, displayController::updateStats);
            fileSendController.setPortOpen(false);

            logController = new LogController(
                    startLoggingButton, stopLoggingButton, logHexToggle, logAsciiToggle,
                    loggingStatusLabel, resolvedStage, logService, displayController::updateStats);
            logController.initialize();
        });

        // ── Wire port state change ──
        toolbarController.setOnPortStateChange((connected, config) -> {
            boolean isConnected = connected != null && connected;
            sendController.setPortOpen(isConnected);
            fileSendController.setPortOpen(isConnected);
            statusBarController.updateConnectionStatus(isConnected, config);
            if (!isConnected) {
                displayController.resetRateCalcs();
                statusBarController.resetRateLabels();
            }
            if (session.getTab() != null) {
                session.getTab().setText(isConnected && config != null
                        ? config.getPortName() : "未连接");
            }
        });
    }

    private ToolBar createPortBar() {
        ComboBox<io.github.serialdebug.core.serial.SerialPortInfo> portCombo = new ComboBox<>();
        portCombo.setPrefWidth(180);
        portCombo.setPromptText("Select port");

        ComboBox<Integer> baudCombo = new ComboBox<>();
        baudCombo.setPrefWidth(100);
        baudCombo.setEditable(true);

        ComboBox<Integer> dataBitsCombo = new ComboBox<>();
        dataBitsCombo.setPrefWidth(60);
        ComboBox<Integer> stopBitsCombo = new ComboBox<>();
        stopBitsCombo.setPrefWidth(60);
        ComboBox<io.github.serialdebug.core.serial.SerialConfig.Parity> parityCombo = new ComboBox<>();
        parityCombo.setPrefWidth(90);

        Button openCloseBtn = new Button("Open", new FontIcon("mdi2p-power-plug"));
        Button refreshBtn = new Button(null, new FontIcon("mdi2r-refresh"));

        Label statusLabel = new Label("Disconnected");
        Label connectionLabel = new Label("Disconnected");

        ToolBar portBar = new ToolBar(
                new Label("Port:"), portCombo, new Separator(),
                new Label("Baud:"), baudCombo, new Label("Data:"), dataBitsCombo,
                new Label("Stop:"), stopBitsCombo, new Label("Parity:"), parityCombo,
                new Separator(), openCloseBtn, refreshBtn);

        toolbarController = new ToolbarController(
                portCombo, baudCombo, dataBitsCombo, stopBitsCombo, parityCombo,
                openCloseBtn, refreshBtn, statusLabel, connectionLabel, serialService);
        toolbarController.initialize();

        return portBar;
    }

    private VBox createStatsPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(8));
        panel.setMinWidth(140);

        Label statusTitle = new Label("Port Status");
        statusTitle.getStyleClass().add("section-title");
        Label statusLabel = new Label("Disconnected");
        statusLabel.setWrapText(true);
        Separator sep1 = new Separator();
        Label statsTitle = new Label("Statistics");
        statsTitle.getStyleClass().add("section-title");
        Label rxCountLabel = new Label("RX: 0 bytes");
        Label txCountLabel = new Label("TX: 0 bytes");
        Label rxRateLabel = new Label("RX rate: 0 B/s");
        Label txRateLabel = new Label("TX rate: 0 B/s");

        panel.getChildren().addAll(statusTitle, statusLabel, sep1,
                statsTitle, rxCountLabel, txCountLabel, rxRateLabel, txRateLabel);

        statusBarController = new StatusBarController(
                null, rxRateLabel, txRateLabel, null, rxRateCalc, txRateCalc);
        statusBarController.initialize();

        return panel;
    }

    private VBox createDisplayArea() {
        VBox right = new VBox(0);

        // ── Display tabs ──
        TabPane displayTabs = new TabPane();
        displayTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(displayTabs, Priority.ALWAYS);

        TextArea hexArea = new TextArea();
        hexArea.setEditable(false);
        hexArea.getStyleClass().add("mono-text-area");
        TextArea asciiArea = new TextArea();
        asciiArea.setEditable(false);
        asciiArea.getStyleClass().add("mono-text-area");

        displayTabs.getTabs().addAll(new Tab("HEX", hexArea), new Tab("ASCII", asciiArea));

        // ── Search bar ──
        TextField searchField = new TextField();
        searchField.setPrefWidth(150);
        searchField.setPromptText("Search...");
        ToggleButton filterToggle = new ToggleButton("Filter");
        ToggleButton caseToggle = new ToggleButton("Aa");
        Button clearBtn = new Button("Clear", new FontIcon("mdi2c-close"));
        Button pauseBtn = new Button("Pause", new FontIcon("mdi2p-pause"));

        ToolBar searchBar = new ToolBar(clearBtn, pauseBtn, new Separator(),
                searchField, filterToggle, caseToggle);

        // ── Send area ──
        VBox sendArea = new VBox(4);
        sendArea.getStyleClass().add("send-area");
        sendArea.setPadding(new Insets(8, 8, 8, 8));

        ToggleButton hexSendToggle = new ToggleButton("HEX");
        hexSendToggle.setSelected(true);
        TextField sendText = new TextField();
        sendText.setPrefHeight(40);
        sendText.setPromptText("Enter data to send...");
        HBox.setHgrow(sendText, Priority.ALWAYS);
        ComboBox<String> lineEndingCombo = new ComboBox<>();
        lineEndingCombo.setPrefWidth(100);
        Button sendBtn = new Button("Send", new FontIcon("mdi2s-send"));
        sendBtn.setDefaultButton(true);

        HBox sendRow1 = new HBox(8, hexSendToggle, sendText, lineEndingCombo, sendBtn);
        sendRow1.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TextField intervalField = new TextField("1000");
        intervalField.setPrefWidth(70);
        TextField countField = new TextField("0");
        countField.setPrefWidth(50);
        Button timerBtn = new Button(null, new FontIcon("mdi2t-timer"));

        HBox sendRow2 = new HBox(8,
                new Label("Interval (ms):"), intervalField,
                new Label("Count:"), countField, timerBtn);
        sendRow2.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // ── File send row (per-session) ──
        fileSendProgress.getStyleClass().add("file-send-progress");
        HBox sendRow3 = new HBox(8, fileSendBtn, fileSendProgress, cancelFileSendBtn);
        sendRow3.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        sendArea.getChildren().addAll(sendRow1, sendRow2, sendRow3);

        right.getChildren().addAll(displayTabs, searchBar, new Separator(), sendArea);

        // ── Controllers ──
        displayController = new DisplayController(
                hexArea, asciiArea, pauseBtn,
                searchField, filterToggle, caseToggle,
                null, null, null, null,
                new HexParser(), new AsciiParser(), new FileLogService(),
                serialService, rxRateCalc, txRateCalc);
        displayController.initialize();

        ListView<io.github.serialdebug.ui.preset.Preset> presetListView = new ListView<>();
        presetListView.setVisible(false);
        Button editPresetsBtn = new Button();
        editPresetsBtn.setVisible(false);

        sendController = new SendController(
                sendText, hexSendToggle, lineEndingCombo, sendBtn,
                intervalField, countField, timerBtn,
                hexArea, asciiArea, presetListView, editPresetsBtn,
                serialService, new HexParser(), new AsciiParser(), new FileLogService(),
                txRateCalc, new JsonPresetService());
        sendController.initialize();

        return right;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(16);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        statusBar.getChildren().addAll(
                new Label("Disconnected"),
                new Pane(), new Label("RX: 0"), new Label("TX: 0"));
        return statusBar;
    }

    public void onStartLogging() {
        if (logController != null) logController.onStartLogging();
    }

    public void onStopLogging() {
        if (logController != null) logController.onStopLogging();
    }

    public void shutdown() {
        if (toolbarController != null && toolbarController.isOpen()) toolbarController.closePort();
        if (sendController != null) sendController.shutdown();
        if (fileSendController != null) fileSendController.shutdown();
        if (statusBarController != null) statusBarController.shutdown();
    }
}