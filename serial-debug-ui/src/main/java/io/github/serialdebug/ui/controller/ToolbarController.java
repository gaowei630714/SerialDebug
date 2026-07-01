package io.github.serialdebug.ui.controller;

import io.github.serialdebug.core.serial.SerialConfig;
import io.github.serialdebug.core.serial.SerialPortInfo;
import io.github.serialdebug.core.serial.SerialService;
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

    private BiConsumer<Boolean, SerialConfig> onPortStateChange;

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
            UiHelper.showError("Failed to list ports", e);
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
            UiHelper.showWarning("Please select a serial port");
            return;
        }
        Integer baudRate = baudRateCombo.getValue();
        if (baudRate == null) { UiHelper.showWarning("Please select baud rate"); return; }
        Integer dataBits = dataBitsCombo.getValue();
        if (dataBits == null) { UiHelper.showWarning("Please select data bits"); return; }
        Integer stopBits = stopBitsCombo.getValue();
        if (stopBits == null) { UiHelper.showWarning("Please select stop bits"); return; }
        SerialConfig.Parity parity = parityCombo.getValue();
        if (parity == null) { UiHelper.showWarning("Please select parity"); return; }

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
            statusLabel.setText("Connected: " + config);
            if (onPortStateChange != null) {
                onPortStateChange.accept(true, config);
            }
        } catch (IOException e) {
            UiHelper.showError("Failed to open port", e);
        }
    }

    public void closePort() {
        if (!isOpen.get()) return;
        try {
            serialService.close();
        } finally {
            isOpen.set(false);
            updatePortState(false);
            statusLabel.setText("Disconnected");
            if (onPortStateChange != null) {
                onPortStateChange.accept(false, null);
            }
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
}
