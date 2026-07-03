package io.github.serialdebug.ui.controller;

import io.github.serialdebug.ui.session.SerialSession;
import io.github.serialdebug.ui.session.SessionManager;
import io.github.serialdebug.ui.session.SessionTabContent;
import io.github.serialdebug.ui.i18n.LocaleManager;
import io.github.serialdebug.ui.i18n.Messages;
import java.util.Locale;
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

    @FXML private MenuBar menuBar;
    @FXML private Menu settingsMenu;
    @FXML private Menu languageMenu;
    @FXML private MenuItem langZhItem;
    @FXML private MenuItem langEnItem;
    @FXML private MenuItem aboutItem;

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
        wireMenuBar();

        Platform.runLater(() -> {
            Stage stage = (Stage) mainTabPane.getScene().getWindow();
            if (stage != null) stage.setOnCloseRequest(e -> sessionManager.closeAll());
            addNewSession();
        });
    }

    private void addNewSession() {
        SerialSession session = sessionManager.createSession();
        Stage stage = (Stage) mainTabPane.getScene().getWindow();
        SessionTabContent content = new SessionTabContent(session, logHexToggle, logAsciiToggle,
                startLoggingButton, stopLoggingButton, loggingStatusLabel, stage);
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
        if (session != null) return session.getTabContent();
        return null;
    }

    private void wireMenuBar() {
        // Wire up language menu
        langZhItem.textProperty().bind(Messages.createStringBinding("lang.chinese"));
        langEnItem.textProperty().bind(Messages.createStringBinding("lang.english"));
        langZhItem.setOnAction(e -> LocaleManager.getInstance().set(Locale.CHINESE));
        langEnItem.setOnAction(e -> LocaleManager.getInstance().set(Locale.ENGLISH));

        // Bind menu titles
        settingsMenu.textProperty().bind(Messages.createStringBinding("menu.settings"));
        languageMenu.textProperty().bind(Messages.createStringBinding("menu.language"));
        aboutItem.textProperty().bind(Messages.createStringBinding("menu.about"));
    }
}
