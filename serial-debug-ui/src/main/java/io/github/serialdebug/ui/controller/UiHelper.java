package io.github.serialdebug.ui.controller;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.application.Platform;

/**
 * Shared UI helpers for sub-controllers.
 */
public final class UiHelper {

    private UiHelper() {}

    public static void showError(String title, Exception e) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage(), ButtonType.OK);
            alert.setTitle(title);
            alert.showAndWait();
        });
    }

    public static void showWarning(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
            alert.setTitle("Warning");
            alert.showAndWait();
        });
    }
}
