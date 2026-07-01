package io.github.serialdebug.ui.controller;

import io.github.serialdebug.ui.session.SerialSession;
import io.github.serialdebug.ui.session.SessionManager;
import io.github.serialdebug.ui.session.SessionTabContent;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Main FXML controller — hosts multi-session TabPane and shared toolbars.
 */
public class MainController implements Initializable {

    @FXML private TabPane mainTabPane;
    @FXML private Tab addTab;

    // Global logging toolbar — affects active session
    @FXML private Button startLoggingButton;
    @FXML private Button stopLoggingButton;
    @FXML private ToggleButton logHexToggle;
    @FXML private ToggleButton logAsciiToggle;
    @FXML private Label loggingStatusLabel;

    private SessionManager sessionManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        sessionManager = new SessionManager(mainTabPane);
        addTab.setClosable(false);

        addTab.setOnSelectionChanged(e -> {
            if (addTab.isSelected()) addNewSession();
        });

        // Tab selection → update active session for logging toolbar
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab != addTab) {
                for (SerialSession s : sessionManager.getSessions()) {
                    if (s.getTab() == newTab) {
                        sessionManager.setActiveSession(s);
                        break;
                    }
                }
            }
        });

        wireLoggingToolbar();

        Platform.runLater(() -> {
            Stage stage = (Stage) mainTabPane.getScene().getWindow();
            if (stage != null) stage.setOnCloseRequest(e -> sessionManager.closeAll());
            addNewSession();
        });
    }

    private void addNewSession() {
        SerialSession session = sessionManager.createSession();
        SessionTabContent content = new SessionTabContent(session, logHexToggle, logAsciiToggle,
                startLoggingButton, stopLoggingButton, loggingStatusLabel);
        session.setTabContent(content);
        session.getTab().setContent(content);
        sessionManager.setActiveSession(session);
    }

    private void wireLoggingToolbar() {
        startLoggingButton.setOnAction(e -> {
            SessionTabContent c = getActiveContent();
            if (c != null) c.onStartLogging();
        });
        stopLoggingButton.setOnAction(e -> {
            SessionTabContent c = getActiveContent();
            if (c != null) c.onStopLogging();
        });
    }

    private SessionTabContent getActiveContent() {
        SerialSession session = sessionManager.getActiveSession();
        if (session != null) return (SessionTabContent) session.getTabContent();
        return null;
    }
}