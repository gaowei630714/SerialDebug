package io.github.serialdebug.ui.controller;

import io.github.serialdebug.core.serial.SerialConfig;
import io.github.serialdebug.core.util.RateCalculator;
import io.github.serialdebug.ui.i18n.Messages;
import io.github.serialdebug.ui.i18n.LocaleManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;

/**
 * Aggregates and displays status bar information: connection status,
 * RX/TX byte rate, and system clock.
 */
public class StatusBarController {

    private final Label connectionStatusLabel;
    private final Label rxRateLabel;
    private final Label txRateLabel;
    private final Label clockLabel;
    private final RateCalculator rxRateCalc;
    private final RateCalculator txRateCalc;

    private Timeline rateClockTimer;

    public StatusBarController(
            Label connectionStatusLabel,
            Label rxRateLabel,
            Label txRateLabel,
            Label clockLabel,
            RateCalculator rxRateCalc,
            RateCalculator txRateCalc) {
        this.connectionStatusLabel = connectionStatusLabel;
        this.rxRateLabel = rxRateLabel;
        this.txRateLabel = txRateLabel;
        this.clockLabel = clockLabel;
        this.rxRateCalc = rxRateCalc;
        this.txRateCalc = txRateCalc;
    }

    public void initialize() {
        var clockFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        if (clockLabel != null) {
            clockLabel.setText(LocalTime.now().format(clockFormatter));
        }

        // Refresh rate labels when locale changes
        LocaleManager.getInstance().localeProperty().addListener((obs, old, locale) -> updateRateLabels());

        // Update rate labels + clock every 1 second
        rateClockTimer = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> updateRateLabels()),
                new KeyFrame(Duration.seconds(1), e -> {
                    if (clockLabel != null) {
                        clockLabel.setText(LocalTime.now().format(clockFormatter));
                    }
                })
        );
        rateClockTimer.setCycleCount(Animation.INDEFINITE);
        rateClockTimer.play();
    }

    public void shutdown() {
        if (rateClockTimer != null) {
            rateClockTimer.stop();
        }
    }

    public void updateConnectionStatus(boolean connected, SerialConfig config) {
        if (connectionStatusLabel == null) return;
        if (connected && config != null) {
            connectionStatusLabel.setText(Messages.get("status.connected") + ": " + config);
        } else {
            connectionStatusLabel.setText(Messages.get("status.disconnected"));
        }
    }

    public void updateRateLabels() {
        double rxRate = rxRateCalc.getRate();
        double txRate = txRateCalc.getRate();
        if (rxRateLabel != null) rxRateLabel.setText(Messages.get("status.rx.rate", rxRate));
        if (txRateLabel != null) txRateLabel.setText(Messages.get("status.tx.rate", txRate));
    }

    public void resetRateLabels() {
        if (rxRateLabel != null) rxRateLabel.setText(Messages.get("status.rx.rate", 0));
        if (txRateLabel != null) txRateLabel.setText(Messages.get("status.tx.rate", 0));
    }
}
