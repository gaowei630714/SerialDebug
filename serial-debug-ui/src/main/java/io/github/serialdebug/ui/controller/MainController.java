package io.github.serialdebug.ui.controller;

import io.github.serialdebug.ui.session.SessionManager;
import io.github.serialdebug.ui.session.SerialSession;
import io.github.serialdebug.ui.session.SessionTabContent;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Main FXML controller — hosts a multi-session TabPane and shared toolbars.
 */
public class MainController implements Initializable {

    @FXML private ToolBar actionBar;
    @FXML private TabPane mainTabPane;
    @FXML private Tab addTab;

    @FXML private Button fileSendButton;
    @FXML private Label fileSendProgress;
    @FXML private Button cancelFileSendButton;
    @FXML private Button startLoggingButton;
    @FXML private Button stopLoggingButton;
    @FXML private ToggleButton logHexToggle;
    @FXML private ToggleButton logAsciiToggle;

    private SessionManager sessionManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Stage stage = (Stage) mainTabPane.getScene().getWindow();

        // Session manager
        sessionManager = new SessionManager(mainTabPane);
        addTab.setClosable(false);

        // Handle "+" tab selection to add new session
        addTab.setOnSelectionChanged(e -> {
            if (addTab.isSelected()) {
                addNewSession();
            }
        });

        // Window close → shutdown
        Platform.runLater(() -> {
            if (stage != null) {
                stage.setOnCloseRequest(e -> sessionManager.closeAll());
            }
        });

        // Create first session
        addNewSession();
    }

    private void addNewSession() {
        SerialSession session = sessionManager.createSession();
        SessionTabContent content = new SessionTabContent(session);
        session.getTab().setContent(content);
    }
}
