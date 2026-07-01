package io.github.serialdebug.ui.session;

import io.github.serialdebug.core.serial.SerialConfig;
import io.github.serialdebug.core.serial.SerialPortInfo;
import io.github.serialdebug.core.serial.SerialService;
import io.github.serialdebug.core.serial.JSerialCommService;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a single serial port session.
 * Each session has its own SerialService, config, and lifecycle.
 */
public class SerialSession {

    private final String sessionId;
    private final SerialService serialService;
    private SerialConfig config;
    private Tab tab;
    private String displayName;
    private Consumer<byte[]> dataListener;

    public SerialSession(String sessionId) {
        this.sessionId = sessionId;
        this.serialService = new JSerialCommService();
    }

    public String getSessionId() { return sessionId; }
    public SerialService getSerialService() { return serialService; }
    public SerialConfig getConfig() { return config; }
    public void setConfig(SerialConfig config) { this.config = config; }
    public Tab getTab() { return tab; }
    public void setTab(Tab tab) { this.tab = tab; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }

    public boolean isOpen() { return serialService.isOpen(); }

    public void setDataListener(Consumer<byte[]> listener) {
        this.dataListener = listener;
        serialService.setDataListener(listener);
    }

    public Consumer<byte[]> getDataListener() { return dataListener; }

    @Override
    public String toString() { return displayName != null ? displayName : sessionId; }
}
