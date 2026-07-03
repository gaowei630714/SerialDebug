package io.github.serialdebug.ui.controller;

import io.github.serialdebug.core.serial.SerialConfig;
import io.github.serialdebug.core.serial.SerialPortInfo;
import io.github.serialdebug.core.serial.SerialService;
import io.github.serialdebug.ui.config.PortHistoryManager;
import io.github.serialdebug.ui.i18n.Messages;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Handles serial port selection, configuration, open/close, and refresh.
 */
public class ToolbarController {

    private final ComboBox<SerialPortInfo> portCombo;
    private final ComboBox<Integer> baudRateCombo;
    private final ComboBox<Integer> dataBitsCombo;
    private final ComboBox<Integer> stopBitsCombo;
    private final ComboBox<SerialConfig.Parity> parityCombo;
    private final Button openCloseButton;
    private final Button refreshButton;
    private final Label statusLabel;
    private final Label connectionStatusLabel;
    private final SerialService serialService;
    private final AtomicBoolean isOpen = new AtomicBoolean(false);
    /** Observable connection state — drives button-text binding and stays in sync with {@link #isOpen}. */
    private final SimpleBooleanProperty connectedProperty = new SimpleBooleanProperty(false);

    private BiConsumer<Boolean, SerialConfig> onPortStateChange;
    private PortHistoryManager historyManager;

    public ToolbarController(
            ComboBox<SerialPortInfo> portCombo,
            ComboBox<Integer> baudRateCombo,
            ComboBox<Integer> dataBitsCombo,
            ComboBox<Integer> stopBitsCombo,
            ComboBox<SerialConfig.Parity> parityCombo,
            Button openCloseButton,
            Button refreshButton,
            Label statusLabel,
            Label connectionStatusLabel,
            SerialService serialService) {
        this.portCombo = portCombo;
        this.baudRateCombo = baudRateCombo;
        this.dataBitsCombo = dataBitsCombo;
        this.stopBitsCombo = stopBitsCombo;
        this.parityCombo = parityCombo;
        this.openCloseButton = openCloseButton;
        this.refreshButton = refreshButton;
        this.statusLabel = statusLabel;
        this.connectionStatusLabel = connectionStatusLabel;
        this.serialService = serialService;

        // Button text reacts to BOTH connection state and locale changes.
        openCloseButton.textProperty().bind(
                Bindings.when(connectedProperty)
                        .then(Messages.createStringBinding("toolbar.close"))
                        .otherwise(Messages.createStringBinding("toolbar.open")));
    }

    public void setHistoryManager(PortHistoryManager historyManager) {
        this.historyManager = historyManager;
        // Listen for port selection changes to auto-fill params
        if (historyManager != null && portCombo != null) {
            portCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, port) -> {
                if (port != null) {
                    historyManager.autoFill(port.getSystemPortName(),
                            baudRateCombo, dataBitsCombo, stopBitsCombo, parityCombo);
                }
            });
        }
    }

    public void setOnPortStateChange(BiConsumer<Boolean, SerialConfig> callback) {
        this.onPortStateChange = callback;
    }

    public void initialize() {
        baudRateCombo.getItems().setAll(300, 600, 1200, 2400, 4800, 9600, 19200, 38400,
                57600, 115200, 230400, 460800, 921600);
        baudRateCombo.getSelectionModel().select(Integer.valueOf(115200));
        dataBitsCombo.getItems().setAll(5, 6, 7, 8);
        dataBitsCombo.getSelectionModel().select(Integer.valueOf(8));
        stopBitsCombo.getItems().setAll(1, 2);
        stopBitsCombo.getSelectionModel().select(Integer.valueOf(1));
        parityCombo.getItems().setAll(SerialConfig.Parity.values());
        parityCombo.getSelectionModel().select(SerialConfig.Parity.NONE);
        refreshPortList();
    }

    public void refreshPortList() {
        try {
            List<SerialPortInfo> ports = serialService.listPorts();
            portCombo.getItems().setAll(ports);
            if (!ports.isEmpty()) {
                portCombo.getSelectionModel().select(0);
            }
        } catch (Exception e) {
            UiHelper.showError(Messages.get("error.list.ports"), e);
        }
    }

    public void onOpenClose() {
        if (isOpen.get()) {
            closePort();
        } else {
            openPort();
        }
    }

    public boolean isOpen() {
        return isOpen.get();
    }

    private void openPort() {
        SerialPortInfo selectedPort = portCombo.getValue();
        if (selectedPort == null) {
            UiHelper.showWarning(Messages.get("warning.select.port"));
            return;
        }
        Integer baudRate = parseComboInt(baudRateCombo, "baud rate");
        if (baudRate == null) return;
        Integer dataBits = dataBitsCombo.getValue();
        if (dataBits == null) { UiHelper.showWarning(Messages.get("warning.select.data.bits")); return; }
        Integer stopBits = stopBitsCombo.getValue();
        if (stopBits == null) { UiHelper.showWarning(Messages.get("warning.select.stop.bits")); return; }
        SerialConfig.Parity parity = parityCombo.getValue();
        if (parity == null) { UiHelper.showWarning(Messages.get("warning.select.parity")); return; }

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
            statusLabel.setText(Messages.get("toolbar.connected") + ": " + config);
            // Save to history for next time
            if (historyManager != null) historyManager.save(config);
            if (onPortStateChange != null) {
                onPortStateChange.accept(true, config);
            }
        } catch (IOException e) {
            UiHelper.showError(Messages.get("warning.open.port"), e);
        }
    }

    public void closePort() {
        if (!isOpen.get()) return;
        try {
            serialService.close();
        } finally {
            isOpen.set(false);
            updatePortState(false);
            statusLabel.setText(Messages.get("toolbar.disconnected"));
            if (onPortStateChange != null) {
                onPortStateChange.accept(false, null);
            }
        }
    }

    /**
     * Safely parse a ComboBox<Integer> value that may be a String (user typed)
     * or Integer (user selected from dropdown).
     */
    private Integer parseComboInt(ComboBox<Integer> combo, String fieldName) {
        Object val = combo.getValue();
        if (val == null) {
            UiHelper.showWarning(Messages.get("warning.select.field", fieldName));
            return null;
        }
        if (val instanceof Integer i) return i;
        if (val instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) {
                UiHelper.showWarning(Messages.get("error.invalid.field", fieldName, s));
                return null;
            }
        }
        UiHelper.showWarning("Invalid " + fieldName);
        return null;
    }

    private void updatePortState(boolean connected) {
        connectedProperty.set(connected);
        if (connected) {
            openCloseButton.getStyleClass().add("btn-danger");
            connectionStatusLabel.setText(Messages.get("toolbar.connected") + ": " + serialService.getCurrentConfig());
        } else {
            openCloseButton.getStyleClass().remove("btn-danger");
            connectionStatusLabel.setText(Messages.get("toolbar.disconnected"));
        }
    }
}
