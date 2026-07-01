package io.github.serialdebug.core.chart;

import java.util.ArrayList;
import java.util.List;

/**
 * Ring buffer storing timestamped numeric data points for waveform rendering.
 * Thread-safe for single-writer, single-reader via synchronized.
 *
 * <p>Each series has its own list of points. Points are pruned when capacity is exceeded.
 */
public class ChartDataBuffer {

    public record DataPoint(long timestampMillis, double value) {}

    private final int capacity;
    private final java.util.Map<String, List<DataPoint>> seriesData = new java.util.LinkedHashMap<>();

    public ChartDataBuffer(int capacity) {
        this.capacity = capacity;
    }

    public ChartDataBuffer() {
        this(10_000); // default 10k points/series
    }

    /**
     * Add a data point to a series. Prunes oldest if over capacity.
     */
    public synchronized void addPoint(String seriesName, double value) {
        List<DataPoint> points = seriesData.computeIfAbsent(seriesName, k -> new ArrayList<>());
        points.add(new DataPoint(System.currentTimeMillis(), value));
        // Prune to capacity
        while (points.size() > capacity) {
            points.remove(0);
        }
    }

    /**
     * Add a data point with explicit timestamp (for replay/testing).
     */
    public synchronized void addPoint(String seriesName, long timestampMillis, double value) {
        List<DataPoint> points = seriesData.computeIfAbsent(seriesName, k -> new ArrayList<>());
        points.add(new DataPoint(timestampMillis, value));
        while (points.size() > capacity) {
            points.remove(0);
        }
    }

    /**
     * Get all points for a series (defensive copy).
     */
    public synchronized List<DataPoint> getSeries(String seriesName) {
        return new ArrayList<>(seriesData.getOrDefault(seriesName, List.of()));
    }

    /**
     * Get all registered series names.
     */
    public synchronized List<String> getSeriesNames() {
        return new ArrayList<>(seriesData.keySet());
    }

    /**
     * Find min/max value across all series (for Y-axis scaling).
     */
    public synchronized double[] getMinMax() {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (List<DataPoint> points : seriesData.values()) {
            for (DataPoint p : points) {
                if (p.value() < min) min = p.value();
                if (p.value() > max) max = p.value();
            }
        }
        return (min == Double.MAX_VALUE) ? new double[]{0, 1} : new double[]{min, max};
    }

    public synchronized void clear() {
        seriesData.clear();
    }

    public synchronized boolean isEmpty() {
        return seriesData.isEmpty() || seriesData.values().stream().allMatch(List::isEmpty);
    }
}
