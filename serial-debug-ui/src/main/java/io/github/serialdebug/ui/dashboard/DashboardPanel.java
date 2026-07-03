package io.github.serialdebug.ui.dashboard;

import io.github.serialdebug.core.chart.DataExtractor;
import io.github.serialdebug.core.chart.DataExtractor.ExtractedValue;
import io.github.serialdebug.ui.i18n.Messages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.TilePane;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard panel: displays metric cards in a TilePane.
 * Reuses a shared DataExtractor instance (same rules as waveform tab).
 */
public class DashboardPanel extends BorderPane {

    private final DataExtractor extractor;
    private final TilePane tilePane;
    private final Map<String, MetricCard> cards = new HashMap<>();

    public DashboardPanel(DataExtractor extractor) {
        this.extractor = extractor;

        Label title = new Label();
        title.textProperty().bind(Messages.createStringBinding("dashboard.title"));
        title.setPadding(new Insets(8, 12, 4, 12));
        title.getStyleClass().add("section-title");
        setTop(title);

        tilePane = new TilePane();
        tilePane.setPadding(new Insets(8));
        tilePane.setHgap(8);
        tilePane.setVgap(8);
        tilePane.setPrefColumns(4);

        ScrollPane scrollPane = new ScrollPane(tilePane);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        setCenter(scrollPane);

        Button clearBtn = new Button();
        clearBtn.textProperty().bind(Messages.createStringBinding("dashboard.clear"));
        clearBtn.setOnAction(e -> resetAll());
        clearBtn.setPadding(new Insets(4, 12, 4, 12));
        BorderPane.setAlignment(clearBtn, Pos.CENTER_RIGHT);
        setBottom(clearBtn);
        setPadding(new Insets(0, 0, 8, 0));
    }

    /**
     * Called by DashboardConsumer when new values are extracted.
     * Must be called on FX thread.
     */
    public void onExtracted(List<ExtractedValue> values) {
        for (ExtractedValue v : values) {
            MetricCard card = cards.computeIfAbsent(
                    v.seriesName(), name -> {
                        MetricCard c = new MetricCard(name);
                        tilePane.getChildren().add(c);
                        return c;
                    });
            card.update(v.value());
        }
    }

    /** Reset all card statistics. */
    public void resetAll() {
        cards.values().forEach(MetricCard::reset);
    }
}
