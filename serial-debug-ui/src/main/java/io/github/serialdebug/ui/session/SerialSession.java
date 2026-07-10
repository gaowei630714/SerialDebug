package io.github.serialdebug.ui.session;

import io.github.serialdebug.core.chart.SessionDataPipeline;
import io.github.serialdebug.core.log.Direction;
import io.github.serialdebug.core.serial.SerialConfig;
import io.github.serialdebug.core.serial.SerialService;
import io.github.serialdebug.core.serial.JSerialCommService;
import javafx.scene.control.Tab;
import java.util.function.Consumer;

/**
 * Represents a single serial port session.
 * Each session has its own {@link SerialService}, {@link SessionDataPipeline},
 * config, and UI tab.
 *
 * <h2>Wiring note — why the listener is attached in the constructor</h2>
 * The {@code dataListener} callback is registered with the {@link SerialService}
 * <em>before</em> {@link SerialService#open} is called. That ordering matters:
 * {@code JSerialCommService.open} checks {@code if (dataListener != null)} and only
 * attaches the live listener to the port when one is already registered.
 *
 * <p>The callback copies every chunk into the pipeline's ring buffer — which is
 * thread-safe (the jSerialComm thread produces, the dispatch timer consumes).</p>
 */
public class SerialSession {

    private final String sessionId;
    private final SerialService serialService;
    private final SessionDataPipeline pipeline;
    private SerialConfig config;
    private Tab tab;
    private String displayName;
    private SessionTabContent tabContent;

    public SerialSession(String sessionId) {
        this.sessionId = sessionId;
        this.serialService = new JSerialCommService();
        this.pipeline = new SessionDataPipeline();
        // Wire serial data into the pipeline. Must happen BEFORE open() so
        // JSerialCommService.open attaches the live listener — see class Javadoc.
        this.serialService.setDataListener(data ->
                pipeline.publish(data, 0, data.length, Direction.RX));
    }

    public String getSessionId() { return sessionId; }
    public SerialService getSerialService() { return serialService; }
    public SessionDataPipeline getPipeline() { return pipeline; }
    public SerialConfig getConfig() { return config; }
    public void setConfig(SerialConfig config) { this.config = config; }
    public Tab getTab() { return tab; }
    public void setTab(Tab tab) { this.tab = tab; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }
    public SessionTabContent getTabContent() { return tabContent; }
    public void setTabContent(SessionTabContent tabContent) { this.tabContent = tabContent; }

    public boolean isOpen() { return serialService.isOpen(); }

    @Override
    public String toString() { return displayName != null ? displayName : sessionId; }
}
