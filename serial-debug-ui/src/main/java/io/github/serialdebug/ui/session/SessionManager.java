package io.github.serialdebug.ui.session;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages the lifecycle of multiple serial sessions across a TabPane.
 */
public class SessionManager {

    private final TabPane tabPane;
    private final List<SerialSession> sessions = new ArrayList<>();
    private final AtomicLong counter = new AtomicLong(0);
    private SerialSession activeSession;

    public SessionManager(TabPane tabPane) {
        this.tabPane = tabPane;
    }

    /**
     * Create a new session and add it as a tab.
     * @return the newly created session
     */
    public SerialSession createSession() {
        String id = "session-" + counter.incrementAndGet();
        SerialSession session = new SerialSession(id);
        sessions.add(session);

        Tab tab = new Tab();
        tab.setText("未连接");
        tab.setClosable(true);
        tab.setOnClosed(e -> closeSession(session));

        // Put a placeholder content — the actual content is the session config UI
        session.setTab(tab);
        tabPane.getTabs().add(tabPane.getTabs().size() - 1, tab); // before "+" tab
        tabPane.getSelectionModel().select(tab);
        activeSession = session;
        return session;
    }

    /**
     * Close and remove a session.
     */
    public void closeSession(SerialSession session) {
        if (session.isOpen()) {
            session.getSerialService().close();
        }
        sessions.remove(session);
        if (tabPane != null && session.getTab() != null) {
            tabPane.getTabs().remove(session.getTab());
        }
        if (activeSession == session) {
            activeSession = sessions.isEmpty() ? null : sessions.get(0);
        }
    }

    /**
     * Get the currently active session.
     */
    public SerialSession getActiveSession() {
        return activeSession;
    }

    /**
     * Set the active session (called when tab selection changes).
     */
    public void setActiveSession(SerialSession session) {
        this.activeSession = session;
    }

    public List<SerialSession> getSessions() {
        return new ArrayList<>(sessions);
    }

    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Close all sessions (e.g. on app exit).
     */
    public void closeAll() {
        for (SerialSession session : new ArrayList<>(sessions)) {
            closeSession(session);
        }
    }
}
