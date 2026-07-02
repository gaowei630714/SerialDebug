package io.github.serialdebug.ui.config;

import io.github.serialdebug.core.serial.SerialConfig;
import javafx.scene.control.ComboBox;

/**
 * Manages port history auto-fill and save-on-connect.
 * Integrates with ToolbarController to restore last-used params per port.
 */
public class PortHistoryManager {

    private final PortHistoryStore store = new PortHistoryStore();

    /**
     * Auto-fill combo boxes when a port is selected, if history exists.
     *
     * @return true if history was found and applied
     */
    public boolean autoFill(String portName,
                            ComboBox<Integer> baudCombo,
                            ComboBox<Integer> dataBitsCombo,
                            ComboBox<Integer> stopBitsCombo,
                            ComboBox<SerialConfig.Parity> parityCombo) {
        var optHistory = store.findByPort(portName);
        if (optHistory.isEmpty()) return false;
        PortHistory history = optHistory.get();

        baudCombo.getSelectionModel().select(history.baudRate());
        dataBitsCombo.getSelectionModel().select(history.dataBits());
        stopBitsCombo.getSelectionModel().select(history.stopBits());
        // Select parity by matching enum name
        for (SerialConfig.Parity p : parityCombo.getItems()) {
            if (p.name().equals(history.parity())) {
                parityCombo.getSelectionModel().select(p);
                break;
            }
        }
        return true;
    }

    /**
     * Save a successful connection to history from a SerialConfig.
     */
    public void save(SerialConfig config) {
        store.save(new PortHistory(
                config.getPortName(), config.getBaudRate(),
                config.getDataBits(), config.getStopBits(),
                config.getParity().name(), config.getFlowControl().name(),
                System.currentTimeMillis()));
    }

    public PortHistoryStore getStore() { return store; }
}
