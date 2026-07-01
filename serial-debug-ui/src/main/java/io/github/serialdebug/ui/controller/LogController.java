package io.github.serialdebug.ui.controller;

import io.github.serialdebug.core.log.LogFormat;
import io.github.serialdebug.core.log.LogService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles log start/stop, format selection, and logging status display.
 */
public class LogController {

    private final Button startLoggingButton;
    private final Button stopLoggingButton;
    private final ToggleButton logHexToggle;
    private final ToggleButton logAsciiToggle;
    private final Label loggingStatusLabel;
    private final Stage stage;
    private final LogService logService;
    private final Runnable onLoggingStatusChanged;

    public LogController(
            Button startLoggingButton,
            Button stopLoggingButton,
            ToggleButton logHexToggle,
            ToggleButton logAsciiToggle,
            Label loggingStatusLabel,
            Stage stage,
            LogService logService,
            Runnable onLoggingStatusChanged) {
        this.startLoggingButton = startLoggingButton;
        this.stopLoggingButton = stopLoggingButton;
        this.logHexToggle = logHexToggle;
        this.logAsciiToggle = logAsciiToggle;
        this.loggingStatusLabel = loggingStatusLabel;
        this.stage = stage;
        this.logService = logService;
        this.onLoggingStatusChanged = onLoggingStatusChanged;
    }

    public void initialize() {
        // Logging format toggles: mutually exclusive
        logHexToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) logAsciiToggle.setSelected(false);
        });
        logAsciiToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) logHexToggle.setSelected(false);
        });
        updateLoggingStatus();
    }

    public void onStartLogging() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Log File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Log Files", "*.log", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        fileChooser.setInitialFileName("serial_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
                ".log");
        File file = fileChooser.showSaveDialog(stage);
        if (file == null) return;

        try {
            LogFormat chosenFormat = logAsciiToggle.isSelected() ? LogFormat.ASCII : LogFormat.HEX;
            logService.start(file.toPath(), chosenFormat);
            startLoggingButton.setDisable(true);
            stopLoggingButton.setDisable(false);
            logHexToggle.setDisable(true);
            logAsciiToggle.setDisable(true);
            updateLoggingStatus();
            if (onLoggingStatusChanged != null) onLoggingStatusChanged.run();
        } catch (IOException e) {
            UiHelper.showError("Failed to start logging", e);
        }
    }

    public void onStopLogging() {
        logService.stop();
        startLoggingButton.setDisable(false);
        stopLoggingButton.setDisable(false);
        logHexToggle.setDisable(false);
        logAsciiToggle.setDisable(false);
        updateLoggingStatus();
        if (onLoggingStatusChanged != null) onLoggingStatusChanged.run();
    }

    public void updateLoggingStatus() {
        final String status;
        if (logService.isLogging()) {
            status = "Recording: " + logService.getCurrentFile().getFileName()
                    + " (" + (logService.getBytesLogged() / 1024) + " KB)";
        } else {
            status = "Not recording";
        }
        final String text = status;
        if (Platform.isFxApplicationThread()) {
            loggingStatusLabel.setText(text);
        } else {
            Platform.runLater(() -> loggingStatusLabel.setText(text));
        }
    }
}
