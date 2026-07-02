package io.github.serialdebug.ui.subtab;

import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages sub-tabs within a session tab. Each sub-tab may register
 * a PayloadConsumer that gets activated on tab selection.
 */
public class SubTabPane extends TabPane {

    private record SubTab(String name, Tab tab, PayloadConsumer consumer,
                          Runnable onShow, Runnable onHide, boolean alwaysActive) {}

    private final Map<Tab, SubTab> tabMap = new HashMap<>();
    private final SessionDataPipeline pipeline;

    public SubTabPane(SessionDataPipeline pipeline) {
        super();
        this.pipeline = pipeline;
        setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) ->
                onTabChanged(oldTab, newTab));
    }

    public void addAlwaysActiveTab(String name, Tab tab, PayloadConsumer consumer) {
        SubTab st = new SubTab(name, tab, consumer, null, null, true);
        tabMap.put(tab, st);
        getTabs().add(tab);
        pipeline.register(consumer);
    }

    public void addLazyTab(String name, Tab tab, PayloadConsumer consumer,
                           Runnable onShow, Runnable onHide) {
        SubTab st = new SubTab(name, tab, consumer, onShow, onHide, false);
        tabMap.put(tab, st);
        getTabs().add(tab);
        if (getSelectionModel().getSelectedItem() == tab) {
            pipeline.register(consumer);
            if (onShow != null) onShow.run();
        }
    }

    private void onTabChanged(Tab oldTab, Tab newTab) {
        if (oldTab != null) {
            SubTab st = tabMap.get(oldTab);
            if (st != null && !st.alwaysActive()) {
                pipeline.unregister(st.consumer());
                if (st.onHide() != null) st.onHide().run();
            }
        }
        if (newTab != null) {
            SubTab st = tabMap.get(newTab);
            if (st != null && !st.alwaysActive()) {
                pipeline.register(st.consumer());
                if (st.onShow() != null) st.onShow().run();
            }
        }
    }

    public void shutdown() {
        for (SubTab st : tabMap.values()) {
            pipeline.unregister(st.consumer());
            if (st.onHide() != null) st.onHide().run();
        }
        tabMap.clear();
    }

    public SessionDataPipeline getPipeline() { return pipeline; }
}