package io.github.serialdebug.ui.dashboard;

import io.github.serialdebug.ui.i18n.LocaleManager;
import io.github.serialdebug.ui.i18n.Messages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * A metric card displaying latest value + Min/Max/Avg statistics.
 * Thread-safe for single-writer (FX thread), single-reader.
 */
public class MetricCard extends VBox {

    private final Label nameLabel;
    private final Label valueLabel;
    private final Label statsLabel;
    private final Label avgLabel;

    private double latest;
    private double min;
    private double max;
    private double sum;
    private int count;

    public MetricCard(String seriesName) {
        setPrefSize(180, 120);
        setMinSize(180, 120);
        setPadding(new Insets(8));
        setSpacing(4);
        getStyleClass().add("metric-card");

        nameLabel = new Label(seriesName);
        nameLabel.getStyleClass().add("metric-card-name");

        valueLabel = new Label("---");
        valueLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 24));
        valueLabel.getStyleClass().add("metric-card-value");

        statsLabel = new Label(Messages.get("dashboard.min") + " ---  "
                + Messages.get("dashboard.max") + " ---");
        statsLabel.getStyleClass().add("metric-card-stats");

        avgLabel = new Label(Messages.get("dashboard.avg") + " ---  "
                + Messages.get("dashboard.n") + " 0");
        avgLabel.getStyleClass().add("metric-card-stats");

        LocaleManager.getInstance().localeProperty().addListener((obs, old, locale) -> {
            if (count > 0) refreshDisplay();
        });

        getChildren().addAll(nameLabel, valueLabel, statsLabel, avgLabel);
        setAlignment(Pos.CENTER_LEFT);
    }

    /** Update card with a new value. Call on FX thread. */
    public void update(double value) {
        latest = value;
        if (count == 0) {
            min = max = value;
        } else {
            if (value < min) min = value;
            if (value > max) max = value;
        }
        sum += value;
        count++;
        refreshDisplay();
    }

    /** Reset all statistics. */
    public void reset() {
        latest = 0;
        min = 0;
        max = 0;
        sum = 0;
        count = 0;
        refreshDisplay();
    }

    public double getLatest() { return latest; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getAverage() { return count > 0 ? sum / count : 0; }
    public int getCount() { return count; }

    private void refreshDisplay() {
        valueLabel.setText(formatValue(latest));
        statsLabel.setText(Messages.get("dashboard.min") + " " + formatValue(min) + "  "
                + Messages.get("dashboard.max") + " " + formatValue(max));
        avgLabel.setText(Messages.get("dashboard.avg") + " " + formatValue(getAverage())
                + "  " + Messages.get("dashboard.n") + " " + count);
    }

    /**
     * Format a double value: integer if whole number, 1 decimal otherwise,
     * scientific notation for extreme values, "---" for Infinity/NaN.
     */
    static String formatValue(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "---";
        if (value == Math.rint(value) && Math.abs(value) < 1e6) {
            return String.format("%.0f", value);
        }
        if (Math.abs(value) >= 1e6 || (Math.abs(value) < 0.01 && value != 0)) {
            return String.format("%.2e", value);
        }
        return String.format("%.1f", value);
    }
}
