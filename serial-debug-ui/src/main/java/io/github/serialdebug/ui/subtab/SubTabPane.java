package io.github.serialdebug.ui.subtab;

import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages sub-tabs within a session tab. Each sub-tab may register
 * a PayloadConsumer that gets activated on tab selection.
 */
public class SubTabPane extends TabPane {

    private record SubTab(String name, Tab tab, PayloadConsumer consumer,
                          Runnable onShow, Runnable onHide, boolean alwaysActive) {}

    private final List<SubTab> subTabs = new ArrayList<>();
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
        subTabs.add(st);
        getTabs().add(tab);
        pipeline.register(consumer);
    }

    public void addLazyTab(String name, Tab tab, PayloadConsumer consumer,
                           Runnable onShow, Runnable onHide) {
        SubTab st = new SubTab(name, tab, consumer, onShow, onHide, false);
        subTabs.add(st);
        getTabs().add(tab);
        if (getSelectionModel().getSelectedItem() == tab) {
            pipeline.register(consumer);
            if (onShow != null) onShow.run();
        }
    }

    private void onTabChanged(Tab oldTab, Tab newTab) {
        if (oldTab != null) {
            for (SubTab st : subTabs) {
                if (st.tab() == oldTab && !st.alwaysActive()) {
                    pipeline.unregister(st.consumer());
                    if (st.onHide() != null) st.onHide().run();
                    break;
                }
            }
        }
        if (newTab != null) {
            for (SubTab st : subTabs) {
                if (st.tab() == newTab && !st.alwaysActive()) {
                    pipeline.register(st.consumer());
                    if (st.onShow() != null) st.onShow().run();
                    break;
                }
            }
        }
    }

    public void shutdown() {
        for (SubTab st : subTabs) {
            pipeline.unregister(st.consumer());
            if (st.onHide() != null) st.onHide().run();
        }
        subTabs.clear();
    }

    public SessionDataPipeline getPipeline() { return pipeline; }
}
