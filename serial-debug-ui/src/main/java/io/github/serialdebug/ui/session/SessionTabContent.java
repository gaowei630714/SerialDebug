package io.github.serialdebug.ui.session;

import io.github.serialdebug.core.log.FileLogService;
import io.github.serialdebug.core.log.LogService;
import io.github.serialdebug.core.parser.HexParser;
import io.github.serialdebug.core.parser.AsciiParser;
import io.github.serialdebug.core.chart.ChartDataBuffer;
import io.github.serialdebug.core.chart.DataExtractor;
import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.serial.SerialService;
import io.github.serialdebug.core.util.RateCalculator;
import io.github.serialdebug.ui.controller.*;
import io.github.serialdebug.ui.subtab.*;
import io.github.serialdebug.ui.crc.CrcPanel;
import io.github.serialdebug.ui.chart.WaveChartCanvas;
import io.github.serialdebug.ui.config.PortHistoryManager;
import io.github.serialdebug.ui.preset.JsonPresetService;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Builds the complete UI content for one serial session within a tab.
 * Uses SubTabPane for module isolation and SessionDataPipeline for data flow.
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

    private final Button fileSendBtn = new Button("Send File", new FontIcon("mdi2f-file"));
    private final Label fileSendProgress = new Label("");
    private final Button cancelFileSendBtn = new Button("Cancel");

    private final ChartDataBuffer waveBuffer = new ChartDataBuffer();
    private final DataExtractor waveExtractor = new DataExtractor();
    private final WaveChartCanvas waveCanvas = new WaveChartCanvas(waveBuffer, 800, 300);
    private AnimationTimer waveTimer;
    private TextArea hexArea;
    private TextArea asciiArea;
    private TextArea getHexArea() { return hexArea; }
    private TextArea getAsciiArea() { return asciiArea; }

    public SessionTabContent(SerialSession session,
                             ToggleButton logHexToggle, ToggleButton logAsciiToggle,
                             Button startLoggingButton, Button stopLoggingButton,
                             Label loggingStatusLabel, Stage stage) {
        this.session = session;
        this.serialService = session.getSerialService();
        buildUI(logHexToggle, logAsciiToggle, startLoggingButton, stopLoggingButton, loggingStatusLabel, stage);
    }

    private void buildUI(ToggleButton logHexToggle, ToggleButton logAsciiToggle,
                         Button startLoggingButton, Button stopLoggingButton,
                         Label loggingStatusLabel, Stage stage) {
        VBox root = new VBox(0);

        ToolBar portBar = createPortBar();
        root.getChildren().add(portBar);

        SubTabPane subTabs = new SubTabPane(session.getPipeline());
        VBox.setVgrow(subTabs, Priority.ALWAYS);

        // Port history manager (auto-fill + save)
        PortHistoryManager historyManager = new PortHistoryManager();
        toolbarController.setHistoryManager(historyManager);

        Tab ioTab = new Tab("收发视图", createIOView());
        subTabs.addAlwaysActiveTab("io", ioTab, new TextConsumer(hexArea, asciiArea, true));

        Tab chartTab = new Tab("波形图", createChartView());
        subTabs.addLazyTab("chart", chartTab,
                new ChartConsumer(waveBuffer, waveExtractor),
                this::startWaveform, this::stopWaveform);

        // CRC calculator tab (lazy, no consumer needed)
        CrcPanel crcPanel = new CrcPanel(hexResult -> {
            if (sendController != null && hexResult != null) {
                sendController.appendToSendField(hexResult);
            }
        });
        Tab crcTab = new Tab("CRC 助手", crcPanel);
        subTabs.addLazyTab("crc", crcTab, new PayloadConsumer() {
            @Override public void onPacket(RawPacket packet) { /* CRC is manual, no auto-consumption */ }
        }, null, null);

        // Connection history tab
        Tab historyTab = new Tab("连接历史", createHistoryView(historyManager));
        subTabs.addLazyTab("history", historyTab, new PayloadConsumer() {
            @Override public void onPacket(RawPacket packet) { /* History is static */ }
        }, null, null);

        Tab atTab = new Tab("AT伴侣", createPlaceholder("AT 指令伴侣 — M3"));
        subTabs.addLazyTab("at", atTab, new AtConsumer(), null, null);

        Tab dashTab = new Tab("仪表盘", createPlaceholder("数据仪表盘 — M3"));
        subTabs.addLazyTab("dash", dashTab, new DashboardConsumer(), null, null);

        root.getChildren().add(subTabs);
        root.getChildren().add(createStatusBar());

        setCenter(root);

        cancelFileSendBtn.setDisable(true);
        cancelFileSendBtn.setVisible(false);
        fileSendController = new FileSendController(
                fileSendBtn, fileSendProgress, cancelFileSendBtn,
                sendController, displayController::updateStats);
        fileSendBtn.setOnAction(e -> {
            if (fileSendController == null) return;
            if (!toolbarController.isOpen()) {
                UiHelper.showWarning("请先打开串口");
                return;
            }
            fileSendController.onFileSend();
        });
        cancelFileSendBtn.setOnAction(e -> {
            if (fileSendController != null) fileSendController.onCancelFileSend();
        });

        logController = new LogController(
                startLoggingButton, stopLoggingButton, logHexToggle, logAsciiToggle,
                loggingStatusLabel, stage, logService, displayController::updateStats);
        logController.initialize();

        toolbarController.setOnPortStateChange((connected, config) -> {
            boolean isConnected = connected != null && connected;
            sendController.setPortOpen(isConnected);
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
        openCloseBtn.setOnAction(e -> toolbarController.onOpenClose());

        Button refreshBtn = new Button(null, new FontIcon("mdi2r-refresh"));
        refreshBtn.setOnAction(e -> toolbarController.refreshPortList());

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

    private VBox createIOView() {
        VBox view = new VBox(0);

        TabPane displayTabs = new TabPane();
        displayTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(displayTabs, Priority.ALWAYS);

        hexArea = new TextArea();
        hexArea.setEditable(false);
        hexArea.getStyleClass().add("mono-text-area");
        asciiArea = new TextArea();
        asciiArea.setEditable(false);
        asciiArea.getStyleClass().add("mono-text-area");
        displayTabs.getTabs().addAll(new Tab("HEX", hexArea), new Tab("ASCII", asciiArea));

        TextField searchField = new TextField();
        searchField.setPrefWidth(150);
        searchField.setPromptText("Search...");
        ToggleButton filterToggle = new ToggleButton("Filter");
        ToggleButton caseToggle = new ToggleButton("Aa");
        Button clearBtn = new Button("Clear", new FontIcon("mdi2c-close"));
        Button pauseBtn = new Button("Pause", new FontIcon("mdi2p-pause"));

        clearBtn.setOnAction(e -> displayController.onClear());
        pauseBtn.setOnAction(e -> displayController.onPauseScroll());

        ToolBar searchBar = new ToolBar(clearBtn, pauseBtn, new Separator(),
                searchField, filterToggle, caseToggle);

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
        sendRow1.setAlignment(Pos.CENTER_LEFT);

        // Send button wired after sendController is created (below)
        Runnable wireSendBtn = () -> sendBtn.setOnAction(e -> {
            if (sendController != null) sendController.onSend();
        });

        TextField intervalField = new TextField("1000");
        intervalField.setPrefWidth(70);
        TextField countField = new TextField("0");
        countField.setPrefWidth(50);
        Button timerBtn = new Button("Timed", new FontIcon("mdi2t-timer"));
        timerBtn.setOnAction(e -> {
            if (sendController != null) sendController.onTimedSend();
        });

        HBox sendRow2 = new HBox(8,
                new Label("Interval (ms):"), intervalField,
                new Label("Count:"), countField, timerBtn);
        sendRow2.setAlignment(Pos.CENTER_LEFT);

        fileSendProgress.getStyleClass().add("file-send-progress");
        HBox sendRow3 = new HBox(8, fileSendBtn, fileSendProgress, cancelFileSendBtn);
        sendRow3.setAlignment(Pos.CENTER_LEFT);

        sendArea.getChildren().addAll(sendRow1, sendRow2, sendRow3);

        view.getChildren().addAll(displayTabs, searchBar, new Separator(), sendArea);

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

        // Wire send button now that controller exists
        wireSendBtn.run();

        return view;
    }

    private VBox createChartView() {
        VBox view = new VBox(4);
        view.setPadding(new Insets(4));

        TextField ruleField = new TextField();
        ruleField.setPromptText("例: T=([\\d.]+)|H=([\\d.]+)");
        HBox.setHgrow(ruleField, Priority.ALWAYS);
        Button applyBtn = new Button("应用");
        applyBtn.setOnAction(e -> {
            waveExtractor.clearRules();
            String text = ruleField.getText();
            if (text != null && !text.isBlank()) {
                for (String rule : text.split("\\|")) {
                    String[] parts = rule.split("=", 2);
                    if (parts.length == 2) {
                        waveExtractor.addRegexRule(parts[0].trim(), parts[1].trim(), 1);
                    }
                }
            }
        });
        Button clearBtn = new Button("清空");
        clearBtn.setOnAction(e -> waveBuffer.clear());
        CheckBox autoScrollCheck = new CheckBox("自动滚动");
        autoScrollCheck.setSelected(true);

        ToolBar chartBar = new ToolBar(new Label("规则:"), ruleField, applyBtn, clearBtn, autoScrollCheck);

        StackPane canvasPane = new StackPane(waveCanvas);
        waveCanvas.widthProperty().bind(canvasPane.widthProperty());
        waveCanvas.heightProperty().bind(canvasPane.heightProperty());
        VBox.setVgrow(canvasPane, Priority.ALWAYS);

        view.getChildren().addAll(chartBar, canvasPane);
        return view;
    }

    private VBox createPlaceholder(String text) {
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER);
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 16px; -fx-text-fill: #999;");
        box.getChildren().add(label);
        return box;
    }

    private VBox createHistoryView(PortHistoryManager historyManager) {
        VBox view = new VBox(8);
        view.setPadding(new Insets(12));

        Label title = new Label("最近连接的设备");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        ListView<String> historyList = new ListView<>();
        historyList.setPrefHeight(300);

        // Load history
        var history = historyManager.getStore().getRecent(20);
        for (var h : history) {
            historyList.getItems().add(String.format("%s @ %d %d-%s-%d",
                    h.portName(), h.baudRate(), h.dataBits(), h.parity().charAt(0), h.stopBits()));
        }

        if (history.isEmpty()) {
            historyList.getItems().add("暂无连接历史");
        }

        view.getChildren().addAll(title, historyList);
        return view;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(16);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(4, 8, 4, 8));

        Label connLabel = new Label("Disconnected");
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label rxRateLabel = new Label("RX rate: 0 B/s");
        Label txRateLabel = new Label("TX rate: 0 B/s");

        statusBar.getChildren().addAll(connLabel, spacer, rxRateLabel, txRateLabel);

        statusBarController = new StatusBarController(
                connLabel, rxRateLabel, txRateLabel, null, rxRateCalc, txRateCalc);
        statusBarController.initialize();

        return statusBar;
    }

    private void startWaveform() {
        if (waveTimer != null) return;
        waveTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                session.getPipeline().dispatch();
                waveCanvas.redraw();
            }
        };
        waveTimer.start();
    }

    private void stopWaveform() {
        if (waveTimer != null) {
            waveTimer.stop();
            waveTimer = null;
        }
    }

    public void onStartLogging() {
        if (logController != null) logController.onStartLogging();
    }

    public void onStopLogging() {
        if (logController != null) logController.onStopLogging();
    }

    public void shutdown() {
        stopWaveform();
        if (toolbarController != null && toolbarController.isOpen()) toolbarController.closePort();
        if (sendController != null) sendController.shutdown();
        if (fileSendController != null) fileSendController.shutdown();
        if (statusBarController != null) statusBarController.shutdown();
    }

    public SerialSession getSession() { return session; }
}
