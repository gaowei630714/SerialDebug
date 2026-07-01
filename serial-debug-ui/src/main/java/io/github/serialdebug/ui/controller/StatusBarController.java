package io.github.serialdebug.ui.controller;

import io.github.serialdebug.core.serial.SerialConfig;
import io.github.serialdebug.core.util.RateCalculator;
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
        if (connected && config != null) {
            connectionStatusLabel.setText("Connected: " + config);
        } else {
            connectionStatusLabel.setText("Disconnected");
        }
    }

    public void updateRateLabels() {
        double rxRate = rxRateCalc.getRate();
        double txRate = txRateCalc.getRate();
        rxRateLabel.setText(String.format("RX rate: %.1f B/s", rxRate));
        txRateLabel.setText(String.format("TX rate: %.1f B/s", txRate));
    }

    public void resetRateLabels() {
        rxRateLabel.setText("RX rate: 0 B/s");
        txRateLabel.setText("TX rate: 0 B/s");
    }
}
