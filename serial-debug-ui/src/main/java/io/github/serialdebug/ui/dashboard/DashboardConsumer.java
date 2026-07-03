package io.github.serialdebug.ui.dashboard;

import io.github.serialdebug.core.chart.DataExtractor;
import io.github.serialdebug.core.chart.DataExtractor.ExtractedValue;
import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.log.Direction;
import javafx.application.Platform;

import java.util.List;
import java.util.function.Consumer;

/**
 * Consumes raw packets from the pipeline, extracts numeric values using
 * a shared DataExtractor, and forwards them to a DashboardPanel callback.
 */
public class DashboardConsumer implements PayloadConsumer {

    private final DataExtractor extractor;
    private final Consumer<List<ExtractedValue>> onExtracted;

    public DashboardConsumer(DataExtractor extractor,
                             Consumer<List<ExtractedValue>> onExtracted) {
        this.extractor = extractor;
        this.onExtracted = onExtracted;
    }

    @Override
    public void onPacket(RawPacket pkt) {
        if (pkt.dir() == Direction.TX) return;
        String text = new String(pkt.data(), pkt.offset(), pkt.length());
        List<ExtractedValue> values = extractor.extract(text);
        if (!values.isEmpty()) {
            List<ExtractedValue> copy = List.copyOf(values);
            Platform.runLater(() -> onExtracted.accept(copy));
        }
    }
}
