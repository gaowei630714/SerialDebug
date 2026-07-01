package io.github.serialdebug.ui.chart;

import io.github.serialdebug.core.chart.ChartDataBuffer;
import io.github.serialdebug.core.chart.ChartDataBuffer.DataPoint;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.List;

/**
 * Self-drawing waveform chart on a JavaFX Canvas.
 * Draws line plots for multiple series, grid, axes, and auto-scaling.
 */
public class WaveChartCanvas extends Canvas {

    private static final Color[] SERIES_COLORS = {
            Color.web("#4a90d9"), // blue
            Color.web("#e74c3c"), // red
            Color.web("#2ecc71"), // green
            Color.web("#f39c12"), // orange
            Color.web("#9b59b6"), // purple
            Color.web("#1abc9c"), // teal
    };

    private static final Color GRID_COLOR = Color.web("#333333");
    private static final Color AXIS_COLOR = Color.web("#666666");
    private static final Color BG_COLOR = Color.web("#1e1e1e");
    private static final Color TEXT_COLOR = Color.web("#888888");

    private final ChartDataBuffer dataBuffer;
    private boolean autoScroll = true;
    private boolean showGrid = true;
    private int viewPointCount = 1000; // how many points to display

    public WaveChartCanvas(ChartDataBuffer dataBuffer, double width, double height) {
        super(width, height);
        this.dataBuffer = dataBuffer;
    }

    public void setAutoScroll(boolean autoScroll) {
        this.autoScroll = autoScroll;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    public void setViewPointCount(int count) {
        this.viewPointCount = Math.max(10, count);
    }

    /**
     * Redraw the chart. Call this on AnimationTimer tick.
     */
    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        // Background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, w, h);

        if (dataBuffer.isEmpty()) {
            gc.setFill(TEXT_COLOR);
            gc.fillText("No data — connect serial port and configure extraction rules", 20, h / 2);
            return;
        }

        double[] minMax = dataBuffer.getMinMax();
        double yMin = minMax[0];
        double yMax = minMax[1];
        // Add 10% padding
        double yRange = (yMax - yMin) * 1.1;
        if (yRange == 0) yRange = 1;
        double yPad = yRange / 2;
        double plotTop = 10;
        double plotBottom = h - 25;
        double plotLeft = 50;
        double plotRight = w - 10;
        double plotHeight = plotBottom - plotTop;

        // Grid
        if (showGrid) {
            drawGrid(gc, plotLeft, plotTop, plotRight, plotBottom, yMin - yPad, yMax + yPad);
        }

        // Axes
        gc.setStroke(AXIS_COLOR);
        gc.strokeLine(plotLeft, plotTop, plotLeft, plotBottom);
        gc.strokeLine(plotLeft, plotBottom, plotRight, plotBottom);

        // Y-axis labels
        gc.setFill(TEXT_COLOR);
        gc.fillText(String.format("%.1f", yMax + yPad), 2, plotTop + 10);
        gc.fillText(String.format("%.1f", yMin - yPad), 2, plotBottom);

        // Draw series
        List<String> seriesNames = dataBuffer.getSeriesNames();
        for (int si = 0; si < seriesNames.size(); si++) {
            String name = seriesNames.get(si);
            List<DataPoint> points = dataBuffer.getSeries(name);
            if (points.size() < 2) continue;

            Color color = SERIES_COLORS[si % SERIES_COLORS.length];
            drawSeries(gc, points, color, plotLeft, plotTop, plotRight, plotBottom,
                    yMin - yPad, yMax + yPad);
        }

        // Legend
        double legendX = plotLeft + 5;
        double legendY = plotTop + 15;
        for (int si = 0; si < seriesNames.size(); si++) {
            gc.setFill(SERIES_COLORS[si % SERIES_COLORS.length]);
            gc.fillRect(legendX, legendY + si * 16, 12, 12);
            gc.setFill(TEXT_COLOR);
            gc.fillText(seriesNames.get(si), legendX + 16, legendY + si * 16 + 11);
        }
    }

    private void drawGrid(GraphicsContext gc, double left, double top, double right, double bottom,
                          double yMin, double yMax) {
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.5);

        // Horizontal grid lines
        int hLines = 5;
        for (int i = 1; i < hLines; i++) {
            double y = top + (bottom - top) * i / hLines;
            gc.strokeLine(left, y, right, y);
        }

        // Vertical grid lines
        int vLines = 8;
        for (int i = 1; i < vLines; i++) {
            double x = left + (right - left) * i / vLines;
            gc.strokeLine(x, top, x, bottom);
        }
    }

    private void drawSeries(GraphicsContext gc, List<DataPoint> points, Color color,
                            double plotLeft, double plotTop, double plotRight, double plotBottom,
                            double yMin, double yMax) {
        gc.setStroke(color);
        gc.setLineWidth(1.5);

        // Determine visible window
        int startIdx = 0;
        int total = points.size();
        if (autoScroll && total > viewPointCount) {
            startIdx = total - viewPointCount;
        }

        double xRange = Math.max(viewPointCount, total);
        double plotWidth = plotRight - plotLeft;
        double plotHeight = plotBottom - plotTop;
        double yRange = yMax - yMin;
        if (yRange == 0) yRange = 1;

        double prevX = -1, prevY = -0;
        for (int i = startIdx; i < total; i++) {
            DataPoint p = points.get(i);
            double x = plotLeft + ((double) (i - startIdx) / (total - startIdx - 1)) * plotWidth;
            double y = plotBottom - ((p.value() - yMin) / yRange) * plotHeight;

            if (prevX >= 0) {
                gc.strokeLine(prevX, prevY, x, y);
            }
            prevX = x;
            prevY = y;
        }
    }
}
