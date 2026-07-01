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

    // Global action bar — targets the active session
    @FXML private Button fileSendButton;
    @FXML private Label fileSendProgress;
    @FXML private Button cancelFileSendButton;
    @FXML private Button startLoggingButton;
    @FXML private Button stopLoggingButton;
    @FXML private ToggleButton logHexToggle;
    @FXML private ToggleButton logAsciiToggle;
    @FXML private Label loggingStatusLabel;

    private SessionManager sessionManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Session manager
        sessionManager = new SessionManager(mainTabPane);
        addTab.setClosable(false);

        // Handle "+" tab selection to add new session
        addTab.setOnSelectionChanged(e -> {
            if (addTab.isSelected()) {
                addNewSession();
            }
        });

        // Tab selection → update global toolbar to target active session
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab != addTab) {
                for (SerialSession s : sessionManager.getSessions()) {
                    if (s.getTab() == newTab) {
                        sessionManager.setActiveSession(s);
                        wireGlobalToolbarToSession(s);
                        break;
                    }
                }
            }
        });

        // Wire global toolbar buttons to forward to active session
        wireGlobalToolbarActions();

        // Defer scene-dependent setup until scene graph is ready
        Platform.runLater(() -> {
            Stage stage = (Stage) mainTabPane.getScene().getWindow();
            if (stage != null) {
                stage.setOnCloseRequest(e -> sessionManager.closeAll());
            }
            addNewSession();
        });
    }

    private void addNewSession() {
        // Save reference to content so we can access it later
        int tabCount = sessionManager.getSessionCount();
        SerialSession session = sessionManager.createSession();
        SessionTabContent content = new SessionTabContent(session);
        session.setTabContent(content);
        session.getTab().setContent(content);

        // Initialize file send + logging controllers with global toolbar controls
        Stage stage = (Stage) mainTabPane.getScene().getWindow();
        content.initFileSendControllers(stage, fileSendButton, fileSendProgress,
                cancelFileSendButton, logHexToggle, logAsciiToggle,
                startLoggingButton, stopLoggingButton, loggingStatusLabel);

        sessionManager.setActiveSession(session);
    }

    /**
     * Wire global toolbar button onAction handlers to forward to the
     * active session's content.
     */
    private void wireGlobalToolbarActions() {
        fileSendButton.setOnAction(e -> {
            SessionTabContent c = getActiveContent();
            if (c != null) c.onFileSend();
        });
        cancelFileSendButton.setOnAction(e -> {
            SessionTabContent c = getActiveContent();
            if (c != null) c.onCancelFileSend();
        });
        startLoggingButton.setOnAction(e -> {
            SessionTabContent c = getActiveContent();
            if (c != null) c.onStartLogging();
        });
        stopLoggingButton.setOnAction(e -> {
            SessionTabContent c = getActiveContent();
            if (c != null) c.onStopLogging();
        });
    }

    /**
     * Called when switching tabs — re-wire session-specific state
     * (e.g. port-open → enable file send).
     */
    private void wireGlobalToolbarToSession(SerialSession session) {
        SessionTabContent content = (SessionTabContent) session.getTabContent();
        if (content != null) {
            content.setPortOpenForExtras(session.isOpen());
        }
    }

    private SessionTabContent getActiveContent() {
        SerialSession session = sessionManager.getActiveSession();
        if (session != null) {
            return (SessionTabContent) session.getTabContent();
        }
        return null;
    }
}
