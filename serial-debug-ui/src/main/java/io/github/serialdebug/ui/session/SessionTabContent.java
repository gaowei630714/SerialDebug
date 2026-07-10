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
import io.github.serialdebug.ui.subtab.TextConsumer;
import io.github.serialdebug.ui.crc.CrcPanel;
import io.github.serialdebug.ui.at.AtCommand;
import io.github.serialdebug.ui.at.AtCommandService;
import io.github.serialdebug.ui.at.JsonAtCommandService;
import io.github.serialdebug.ui.dashboard.DashboardPanel;
import io.github.serialdebug.ui.dashboard.DashboardConsumer;
import io.github.serialdebug.ui.chart.WaveChartCanvas;
import io.github.serialdebug.ui.config.PortHistoryManager;
import io.github.serialdebug.ui.preset.JsonPresetService;
import io.github.serialdebug.ui.i18n.Messages;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final HexParser hexParser = new HexParser();
    private final AsciiParser asciiParser = new AsciiParser();
    private final RateCalculator rxRateCalc = new RateCalculator();
    private final RateCalculator txRateCalc = new RateCalculator();

    private final Button fileSendBtn = new Button();
    private final Label fileSendProgress = new Label("");
    private final Button cancelFileSendBtn = new Button();

    private final ChartDataBuffer waveBuffer = new ChartDataBuffer();
    private final DataExtractor waveExtractor = new DataExtractor();
    private final WaveChartCanvas waveCanvas = new WaveChartCanvas(waveBuffer, 800, 300);
    private AnimationTimer waveTimer;

    private static final Logger LOG = LoggerFactory.getLogger(SessionTabContent.class);

    /**
     * Always-on dispatch pump. Drains {@code session.getPipeline()} on every FX pulse
     * so received bytes reach the HEX/ASCII view in real time on the main IO tab.
     *
     * <p>Owned by the <em>port-session lifecycle</em> (start on port-open, stop on
     * port-close), NOT by the waveform chart tab. Before this fix, dispatch() was
     * only called from {@link #startWaveform()}, so data silently piled up in the
     * ring buffer unless the user happened to have the chart tab open.</p>
     */
    private AnimationTimer ioPump;
    /** 100ms ticker that pushes RX/TX byte counters into the search-bar stats label. */
    private Timeline statsTimeline;
    /**
     * The TextConsumer for this session's IO view, kept so the search-bar stats
     * label can be wired up and refreshed. Created in {@link #createIOView()}.
     */
    private TextConsumer textConsumer;
    private SubTabPane subTabs;
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

        subTabs = new SubTabPane(session.getPipeline());
        VBox.setVgrow(subTabs, Priority.ALWAYS);

        // Port history manager (auto-fill + save)
        PortHistoryManager historyManager = new PortHistoryManager();
        toolbarController.setHistoryManager(historyManager);

        Tab ioTab = new Tab();
        ioTab.textProperty().bind(Messages.createStringBinding("io.tab.receive"));
        ioTab.setContent(createIOView());
        // Capture the consumer so we can wire its stats label into the search bar.
        this.textConsumer = new TextConsumer(hexArea, asciiArea, true);
        subTabs.addAlwaysActiveTab("io", ioTab, textConsumer);

        Tab chartTab = new Tab();
        chartTab.textProperty().bind(Messages.createStringBinding("tab.chart"));
        chartTab.setContent(createChartView());
        subTabs.addLazyTab("chart", chartTab,
                new ChartConsumer(waveBuffer, waveExtractor),
                this::startWaveform, this::stopWaveform);

        // CRC calculator tab (lazy, no consumer needed)
        CrcPanel crcPanel = new CrcPanel(hexResult -> {
            if (sendController != null && hexResult != null) {
                sendController.appendToSendField(hexResult);
            }
        });
        Tab crcTab = new Tab();
        crcTab.textProperty().bind(Messages.createStringBinding("tab.crc"));
        crcTab.setContent(crcPanel);
        subTabs.addLazyTab("crc", crcTab, new PayloadConsumer() {
            @Override public void onPacket(RawPacket packet) { /* CRC is manual, no auto-consumption */ }
        }, null, null);

        // Connection history tab
        Tab historyTab = new Tab();
        historyTab.textProperty().bind(Messages.createStringBinding("tab.history"));
        historyTab.setContent(createHistoryView(historyManager));
        subTabs.addLazyTab("history", historyTab, new PayloadConsumer() {
            @Override public void onPacket(RawPacket packet) { /* History is static */ }
        }, null, null);

        // Dashboard tab (lazy, registers DashboardConsumer)
        DashboardPanel dashboardPanel = new DashboardPanel(waveExtractor);
        Tab dashTab = new Tab();
        dashTab.textProperty().bind(Messages.createStringBinding("tab.dashboard"));
        dashTab.setContent(dashboardPanel);
        subTabs.addLazyTab("dash", dashTab,
                new DashboardConsumer(waveExtractor, dashboardPanel::onExtracted),
                null, null);

        root.getChildren().add(subTabs);
        root.getChildren().add(createStatusBar());

        setCenter(root);

        cancelFileSendBtn.setDisable(true);
        cancelFileSendBtn.setVisible(false);
        fileSendController = new FileSendController(
                fileSendBtn, fileSendProgress, cancelFileSendBtn,
                sendController, displayController::updateStats);
        fileSendBtn.textProperty().bind(Messages.createStringBinding("io.file.send"));
        fileSendBtn.setGraphic(new FontIcon("mdi2f-file"));
        cancelFileSendBtn.textProperty().bind(Messages.createStringBinding("io.file.cancel"));
        fileSendBtn.setOnAction(e -> {
            if (fileSendController == null) return;
            if (!toolbarController.isOpen()) {
                UiHelper.showWarning(Messages.get("warning.open.port.first"));
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
            // Drive the always-on dispatch pump from the port lifecycle, so the HEX/ASCII
            // view updates in real time regardless of which sub-tab is active.
            if (isConnected) {
                startIoPump();
            } else {
                stopIoPump();
            }
            if (!isConnected) {
                displayController.resetRateCalcs();
                statusBarController.resetRateLabels();
            }
            if (session.getTab() != null) {
                session.getTab().setText(isConnected && config != null
                        ? config.getPortName() : Messages.get("io.tab.disconnected"));
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

        Button openCloseBtn = new Button();
        openCloseBtn.setGraphic(new FontIcon("mdi2p-power-plug"));
        openCloseBtn.setOnAction(e -> toolbarController.onOpenClose());

        Button refreshBtn = new Button(null, new FontIcon("mdi2r-refresh"));
        refreshBtn.setOnAction(e -> toolbarController.refreshPortList());

        Label statusLabel = new Label(Messages.get("toolbar.disconnected"));
        Label connectionLabel = new Label(Messages.get("toolbar.disconnected"));

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
        Tab hexTab = new Tab();
        hexTab.textProperty().bind(Messages.createStringBinding("io.tab.hex"));
        hexTab.setContent(hexArea);
        Tab asciiTab = new Tab();
        asciiTab.textProperty().bind(Messages.createStringBinding("io.tab.ascii"));
        asciiTab.setContent(asciiArea);
        displayTabs.getTabs().addAll(hexTab, asciiTab);

        ToggleButton atToggle = new ToggleButton();
        atToggle.textProperty().bind(Messages.createStringBinding("io.at"));
        Tooltip atTooltip = new Tooltip();
        atTooltip.textProperty().bind(Messages.createStringBinding("io.at.tooltip"));
        atToggle.setTooltip(atTooltip);

        TextField searchField = new TextField();
        searchField.setPrefWidth(150);
        searchField.promptTextProperty().bind(Messages.createStringBinding("io.search"));
        ToggleButton filterToggle = new ToggleButton();
        filterToggle.textProperty().bind(Messages.createStringBinding("io.filter"));
        ToggleButton caseToggle = new ToggleButton();
        caseToggle.textProperty().bind(Messages.createStringBinding("io.case"));
        Button clearBtn = new Button();
        clearBtn.textProperty().bind(Messages.createStringBinding("io.clear"));
        clearBtn.setGraphic(new FontIcon("mdi2c-close"));
        Button pauseBtn = new Button();
        pauseBtn.textProperty().bind(Messages.createStringBinding("io.pause"));
        pauseBtn.setGraphic(new FontIcon("mdi2p-pause"));

        clearBtn.setOnAction(e -> displayController.onClear());
        pauseBtn.setOnAction(e -> displayController.onPauseScroll());

        // RX/TX byte stats live at the far right of the search bar, separated from
        // the controls. Refreshed by statsTimeline (100ms) — not per packet.
        Pane statsSpacer = new Pane();
        HBox.setHgrow(statsSpacer, Priority.ALWAYS);
        ToolBar searchBar = new ToolBar(clearBtn, pauseBtn, atToggle, new Separator(),
                searchField, filterToggle, caseToggle, statsSpacer, textConsumer.getStatsLabel());
        textConsumer.getStatsLabel().getStyleClass().add("stats-label");

        // 100ms refresh ticker for the stats label. Indefinite while the tab is alive.
        statsTimeline = new Timeline(new KeyFrame(Duration.millis(100), e -> textConsumer.refreshStats()));
        statsTimeline.setCycleCount(Timeline.INDEFINITE);
        statsTimeline.play();

        VBox sendArea = new VBox(4);
        sendArea.getStyleClass().add("send-area");
        sendArea.setPadding(new Insets(8, 8, 8, 8));

        ToggleButton hexSendToggle = new ToggleButton();
        hexSendToggle.textProperty().bind(Messages.createStringBinding("io.hex.mode"));
        hexSendToggle.setSelected(true);
        TextField sendText = new TextField();
        sendText.setPrefHeight(40);
        sendText.promptTextProperty().bind(Messages.createStringBinding("io.send.prompt"));
        HBox.setHgrow(sendText, Priority.ALWAYS);
        ComboBox<String> lineEndingCombo = new ComboBox<>();
        lineEndingCombo.setPrefWidth(100);
        Button sendBtn = new Button();
        sendBtn.textProperty().bind(Messages.createStringBinding("io.send"));
        sendBtn.setGraphic(new FontIcon("mdi2s-send"));
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
        Button timerBtn = new Button();
        timerBtn.textProperty().bind(Messages.createStringBinding("io.timed"));
        timerBtn.setGraphic(new FontIcon("mdi2t-timer"));
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

        // AT template sidebar (embedded in IO tab's send area)
        VBox atSidebar = new VBox(6);
        atSidebar.setPrefWidth(220);
        atSidebar.setMinWidth(220);
        atSidebar.setPadding(new Insets(6));
        atSidebar.setStyle("-fx-border-color: #ddd; -fx-border-width: 0 0 0 1;");
        atSidebar.setVisible(false);
        atSidebar.managedProperty().bind(atSidebar.visibleProperty());

        Label atTitle = new Label();
        atTitle.textProperty().bind(Messages.createStringBinding("sidebar.title"));
        atTitle.getStyleClass().add("section-title");

        TextField atSearch = new TextField();
        atSearch.promptTextProperty().bind(Messages.createStringBinding("sidebar.search"));

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

        Button atAddBtn = new Button();
        atAddBtn.textProperty().bind(Messages.createStringBinding("sidebar.add"));
        atAddBtn.setOnAction(e -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.titleProperty().bind(Messages.createStringBinding("sidebar.add.dialog.title"));
            DialogPane dialogPane = dialog.getDialogPane();
            dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            TextField nameField = new TextField();
            nameField.promptTextProperty().bind(Messages.createStringBinding("sidebar.add.name"));
            TextField cmdField = new TextField();
            cmdField.promptTextProperty().bind(Messages.createStringBinding("sidebar.add.command"));
            TextField descField = new TextField();
            descField.promptTextProperty().bind(Messages.createStringBinding("sidebar.add.description"));

            Label nameLabel = new Label();
            nameLabel.textProperty().bind(Messages.createStringBinding("sidebar.add.name"));
            Label cmdLabel = new Label();
            cmdLabel.textProperty().bind(Messages.createStringBinding("sidebar.add.command"));
            Label descLabel = new Label();
            descLabel.textProperty().bind(Messages.createStringBinding("sidebar.add.description"));
            VBox content = new VBox(8,
                    nameLabel, nameField,
                    cmdLabel, cmdField,
                    descLabel, descField);
            content.setPadding(new Insets(12));
            dialogPane.setContent(content);

            dialog.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK && !nameField.getText().isBlank() && !cmdField.getText().isBlank()) {
                    atCommands.add(new AtCommand(
                            nameField.getText().trim(), cmdField.getText().trim(), descField.getText().trim()));
                    atService.save(atCommands);
                }
            });
        });

        Button atDelBtn = new Button();
        atDelBtn.textProperty().bind(Messages.createStringBinding("sidebar.delete"));
        atDelBtn.setOnAction(e -> {
            AtCommand sel = atList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                atCommands.remove(sel);
                atService.save(atCommands);
            }
        });

        HBox atButtons = new HBox(6, atAddBtn, atDelBtn);
        atSidebar.getChildren().addAll(atTitle, atSearch, atList, atButtons);

        atToggle.selectedProperty().addListener((obs, old, val) -> atSidebar.setVisible(val));

        BorderPane bottomArea = new BorderPane();
        bottomArea.setCenter(sendArea);
        bottomArea.setRight(atSidebar);
        view.getChildren().addAll(displayTabs, searchBar, new Separator(), bottomArea);

        displayController = new DisplayController(
                hexArea, asciiArea, pauseBtn,
                searchField, filterToggle, caseToggle,
                null, null, null, null,
                hexParser, asciiParser, logService,
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
                serialService, hexParser, asciiParser, logService,
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
        ruleField.promptTextProperty().bind(Messages.createStringBinding("waveform.placeholder"));
        HBox.setHgrow(ruleField, Priority.ALWAYS);
        Button applyBtn = new Button();
        applyBtn.textProperty().bind(Messages.createStringBinding("waveform.apply"));
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
        Button clearBtn = new Button();
        clearBtn.textProperty().bind(Messages.createStringBinding("waveform.clear"));
        clearBtn.setOnAction(e -> waveBuffer.clear());
        CheckBox autoScrollCheck = new CheckBox();
        autoScrollCheck.textProperty().bind(Messages.createStringBinding("waveform.auto.scroll"));
        autoScrollCheck.setSelected(true);

        Label ruleLabel = new Label();
        ruleLabel.textProperty().bind(Messages.createStringBinding("waveform.rule"));
        ToolBar chartBar = new ToolBar(ruleLabel, ruleField, applyBtn, clearBtn, autoScrollCheck);

        StackPane canvasPane = new StackPane(waveCanvas);
        waveCanvas.widthProperty().bind(canvasPane.widthProperty());
        waveCanvas.heightProperty().bind(canvasPane.heightProperty());
        VBox.setVgrow(canvasPane, Priority.ALWAYS);

        view.getChildren().addAll(chartBar, canvasPane);
        return view;
    }

    private VBox createHistoryView(PortHistoryManager historyManager) {
        VBox view = new VBox(8);
        view.setPadding(new Insets(12));

        Label title = new Label();
        title.textProperty().bind(Messages.createStringBinding("history.title"));
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
            historyList.getItems().add(Messages.get("history.empty"));
        }

        view.getChildren().addAll(title, historyList);
        return view;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(16);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(4, 8, 4, 8));

        Label connLabel = new Label(Messages.get("status.disconnected"));
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label rxRateLabel = new Label(Messages.get("status.rx.rate", 0));
        Label txRateLabel = new Label(Messages.get("status.tx.rate", 0));

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
                // dispatch() is now owned by ioPump (port-open lifecycle); the
                // waveform tab only needs to redraw its canvas each pulse.
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

    private void startIoPump() {
        if (ioPump != null) return;
        ioPump = new AnimationTimer() {
            @Override
            public void handle(long now) {
                int n = session.getPipeline().dispatch();
                if (n > 0) {
                    LOG.debug("ioPump dispatched {} packet(s)", n);
                }
            }
        };
        ioPump.start();
        LOG.debug("ioPump started");
    }

    private void stopIoPump() {
        if (ioPump != null) {
            ioPump.stop();
            ioPump = null;
            LOG.debug("ioPump stopped");
        }
    }

    public void onStartLogging() {
        if (logController != null) logController.onStartLogging();
    }

    public void onStopLogging() {
        if (logController != null) logController.onStopLogging();
    }

    public void shutdown() {
        stopIoPump();
        stopWaveform();
        if (statsTimeline != null) {
            statsTimeline.stop();
            statsTimeline = null;
        }
        if (textConsumer != null) {
            textConsumer.resetStats();
        }
        if (toolbarController != null && toolbarController.isOpen()) toolbarController.closePort();
        if (sendController != null) sendController.shutdown();
        if (fileSendController != null) fileSendController.shutdown();
        if (statusBarController != null) statusBarController.shutdown();
    }

    public SerialSession getSession() { return session; }
}
