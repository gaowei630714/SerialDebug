package io.github.serialdebug.ui.chart;

import io.github.serialdebug.core.chart.ChartDataBuffer;
import io.github.serialdebug.core.chart.DataExtractor;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;

/**
 * Connects a serial session's data flow to a WaveChartCanvas.
 * Manages the DataExtractor rules, data buffer, and rendering timer.
 */
public class WaveformController {

    private final ChartDataBuffer dataBuffer;
    private final DataExtractor dataExtractor;
    private final WaveChartCanvas canvas;
    private AnimationTimer renderTimer;

    public WaveformController(Canvas canvasPlaceholder) {
        this.dataBuffer = new ChartDataBuffer(10_000);
        this.dataExtractor = new DataExtractor();
        this.canvas = new WaveChartCanvas(dataBuffer, 800, 300);
    }

    /**
     * Process incoming serial data: extract values and add to buffer.
     */
    public void onDataReceived(byte[] data) {
        // Try text extraction first
        String text = new String(data);
        var values = dataExtractor.extract(text);
        for (var v : values) {
            dataBuffer.addPoint(v.seriesName(), v.value());
        }
    }

    /**
     * Add a regex extraction rule.
     */
    public void addRegexRule(String seriesName, String regex, int groupIndex) {
        dataExtractor.addRegexRule(seriesName, regex, groupIndex);
    }

    /**
     * Configure rules from a simple config string: "name=regex|name=regex"
     */
    public void configureFromText(String rules) {
        if (rules == null || rules.isBlank()) return;
        for (String rule : rules.split("\\|")) {
            String[] parts = rule.split("=", 2);
            if (parts.length == 2) {
                addRegexRule(parts[0].trim(), parts[1].trim(), 1);
            }
        }
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public ChartDataBuffer getDataBuffer() {
        return dataBuffer;
    }

    public DataExtractor getDataExtractor() {
        return dataExtractor;
    }

    /**
     * Start the rendering timer (60fps target).
     */
    public void start() {
        renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                canvas.redraw();
            }
        };
        renderTimer.start();
    }

    /**
     * Stop the rendering timer.
     */
    public void stop() {
        if (renderTimer != null) {
            renderTimer.stop();
            renderTimer = null;
        }
    }

    /**
     * Clear all chart data.
     */
    public void clear() {
        dataBuffer.clear();
    }

    public void setAutoScroll(boolean autoScroll) {
        canvas.setAutoScroll(autoScroll);
    }
}
