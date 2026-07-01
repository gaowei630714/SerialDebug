package io.github.serialdebug.ui.controller;

import io.github.serialdebug.core.parser.AsciiParser;
import io.github.serialdebug.core.parser.DataParser;
import io.github.serialdebug.core.parser.HexParser;
import io.github.serialdebug.core.log.Direction;
import io.github.serialdebug.core.log.LogService;
import io.github.serialdebug.core.serial.SerialService;
import io.github.serialdebug.core.util.RateCalculator;
import javafx.application.Platform;
import javafx.scene.control.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Handles data display: onDataReceived, batch flush, search/filter,
 * pause scroll, clear, and RX rate stats.
 */
public class DisplayController {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final TextArea hexViewArea;
    private final TextArea asciiViewArea;
    private final Button pauseScrollButton;
    private final TextField searchField;
    private final ToggleButton filterModeToggle;
    private final ToggleButton caseSensitiveToggle;
    private final Label rxCountLabel;
    private final Label txCountLabel;
    private final Label statusRxLabel;
    private final Label statusTxLabel;

    private final DataParser hexParser;
    private final DataParser asciiParser;
    private final LogService logService;
    private final SerialService serialService;
    private final RateCalculator rxRateCalc;
    private final RateCalculator txRateCalc;

    private volatile boolean autoScrollPaused = false;
    private final List<String> hexLineBuffer = new ArrayList<>();
    private final List<String> asciiLineBuffer = new ArrayList<>();
    private final Object bufferLock = new Object();

    private record BatchEntry(String timestamp, String hex, String ascii, Direction dir) {}

    private final List<BatchEntry> batchBuffer = new ArrayList<>();
    private final AtomicBoolean batchPending = new AtomicBoolean(false);

    private Consumer<Boolean> onAutoScrollPaused;

    public DisplayController(
            TextArea hexViewArea,
            TextArea asciiViewArea,
            Button pauseScrollButton,
            TextField searchField,
            ToggleButton filterModeToggle,
            ToggleButton caseSensitiveToggle,
            Label rxCountLabel,
            Label txCountLabel,
            Label statusRxLabel,
            Label statusTxLabel,
            DataParser hexParser,
            DataParser asciiParser,
            LogService logService,
            SerialService serialService,
            RateCalculator rxRateCalc,
            RateCalculator txRateCalc) {
        this.hexViewArea = hexViewArea;
        this.asciiViewArea = asciiViewArea;
        this.pauseScrollButton = pauseScrollButton;
        this.searchField = searchField;
        this.filterModeToggle = filterModeToggle;
        this.caseSensitiveToggle = caseSensitiveToggle;
        this.rxCountLabel = rxCountLabel;
        this.txCountLabel = txCountLabel;
        this.statusRxLabel = statusRxLabel;
        this.statusTxLabel = statusTxLabel;
        this.hexParser = hexParser;
        this.asciiParser = asciiParser;
        this.logService = logService;
        this.serialService = serialService;
        this.rxRateCalc = rxRateCalc;
        this.txRateCalc = txRateCalc;
    }

    public void setOnAutoScrollPaused(Consumer<Boolean> callback) {
        this.onAutoScrollPaused = callback;
    }

    public void initialize() {
        // Search/filter listeners
        searchField.textProperty().addListener((obs, old, val) -> applyFilter());
        filterModeToggle.selectedProperty().addListener((obs, old, val) -> applyFilter());
        caseSensitiveToggle.selectedProperty().addListener((obs, old, val) -> {
            if (filterModeToggle.isSelected()) applyFilter();
        });
    }

    public void onClear() {
        hexViewArea.clear();
        asciiViewArea.clear();
        synchronized (bufferLock) {
            hexLineBuffer.clear();
            asciiLineBuffer.clear();
        }
    }

    public void onPauseScroll() {
        autoScrollPaused = !autoScrollPaused;
        if (autoScrollPaused) {
            pauseScrollButton.setText("Resume");
            pauseScrollButton.getStyleClass().add("btn-warning");
        } else {
            pauseScrollButton.setText("Pause");
            pauseScrollButton.getStyleClass().remove("btn-warning");
            hexViewArea.setScrollTop(Double.MAX_VALUE);
            asciiViewArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    public boolean isAutoScrollPaused() {
        return autoScrollPaused;
    }

    public void resetRateCalcs() {
        rxRateCalc.reset();
        txRateCalc.reset();
    }

    // ── Data Reception ───────────────────────────────────────────────────

    public void onDataReceived(byte[] data) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String hexStr = hexParser.decode(data, 0, data.length);
        String asciiStr = asciiParser.decode(data, 0, data.length);

        rxRateCalc.addSample(data.length);

        // Write log on listener thread (not UI thread)
        if (logService.isLogging()) {
            logService.log(data, 0, data.length, Direction.RX);
        }

        // Batch UI update: coalesce multiple rapid events into one Platform.runLater
        synchronized (batchBuffer) {
            batchBuffer.add(new BatchEntry(timestamp, hexStr, asciiStr, Direction.RX));
        }
        if (batchPending.compareAndSet(false, true)) {
            Platform.runLater(this::flushBatch);
        }
    }

    private void flushBatch() {
        List<BatchEntry> entries;
        synchronized (batchBuffer) {
            entries = new ArrayList<>(batchBuffer);
            batchBuffer.clear();
        }
        for (BatchEntry e : entries) {
            if (hexViewArea.getLength() > 1_000_000) {
                hexViewArea.clear();
                synchronized (bufferLock) { hexLineBuffer.clear(); }
            }
            if (asciiViewArea.getLength() > 1_000_000) {
                asciiViewArea.clear();
                synchronized (bufferLock) { asciiLineBuffer.clear(); }
            }
            hexViewArea.appendText("[" + e.timestamp + " " + e.dir + "] " + e.hex + "\n");
            asciiViewArea.appendText("[" + e.timestamp + " " + e.dir + "] " + e.ascii + "\n");
        }
        // Always buffer for search/filter
        synchronized (bufferLock) {
            for (BatchEntry e : entries) {
                String hexLine = "[" + e.timestamp + " " + e.dir + "] " + e.hex;
                String asciiLine = "[" + e.timestamp + " " + e.dir + "] " + e.ascii;
                hexLineBuffer.add(hexLine);
                asciiLineBuffer.add(asciiLine);
            }
        }
        if (!autoScrollPaused) {
            hexViewArea.setScrollTop(Double.MAX_VALUE);
            asciiViewArea.setScrollTop(Double.MAX_VALUE);
        }
        updateStats();
        batchPending.set(false);
        // Re-arm if new data arrived during flush (race-safe)
        synchronized (batchBuffer) {
            if (!batchBuffer.isEmpty() && batchPending.compareAndSet(false, true)) {
                Platform.runLater(this::flushBatch);
            }
        }
    }

    // ── Search/Filter ─────────────────────────────────────────────────────

    public void applyFilter() {
        // Called from FX listeners — already on FX thread, no Platform.runLater needed
        String query = searchField.getText();
        StringBuilder filteredHex = new StringBuilder();
        StringBuilder filteredAscii = new StringBuilder();
        synchronized (bufferLock) {
            for (int i = 0; i < hexLineBuffer.size(); i++) {
                String hexLine = hexLineBuffer.get(i);
                String asciiLine = asciiLineBuffer.get(i);
                if (!filterModeToggle.isSelected() || query == null || query.isEmpty()) {
                    // Filter off — include all lines
                    filteredHex.append(hexLine).append('\n');
                    filteredAscii.append(asciiLine).append('\n');
                } else {
                    boolean matches;
                    if (caseSensitiveToggle.isSelected()) {
                        matches = hexLine.contains(query) || asciiLine.contains(query);
                    } else {
                        matches = hexLine.toLowerCase().contains(query.toLowerCase())
                                || asciiLine.toLowerCase().contains(query.toLowerCase());
                    }
                    if (matches) {
                        filteredHex.append(hexLine).append('\n');
                        filteredAscii.append(asciiLine).append('\n');
                    }
                }
            }
        }
        hexViewArea.setText(filteredHex.toString());
        asciiViewArea.setText(filteredAscii.toString());
        if (!autoScrollPaused) {
            hexViewArea.setScrollTop(Double.MAX_VALUE);
            asciiViewArea.setScrollTop(Double.MAX_VALUE);
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    public void updateStats() {
        Platform.runLater(() -> {
            long rx = serialService.getBytesReceived();
            long tx = serialService.getBytesSent();
            if (rxCountLabel != null) rxCountLabel.setText("RX: " + rx + " bytes");
            if (txCountLabel != null) txCountLabel.setText("TX: " + tx + " bytes");
            if (statusRxLabel != null) statusRxLabel.setText("RX: " + rx);
            if (statusTxLabel != null) statusTxLabel.setText("TX: " + tx);
        });
    }
}
