package io.github.serialdebug.ui.subtab;

import io.github.serialdebug.core.chart.ChartDataBuffer;
import io.github.serialdebug.core.chart.DataExtractor;
import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.log.Direction;

/**
 * Consumes raw packets and adds extracted numeric values to ChartDataBuffer.
 * Supports both text regex extraction and binary offset extraction.
 */
public class ChartConsumer implements PayloadConsumer {

    private final ChartDataBuffer dataBuffer;
    private final DataExtractor extractor;

    public ChartConsumer(ChartDataBuffer dataBuffer, DataExtractor extractor) {
        this.dataBuffer = dataBuffer;
        this.extractor = extractor;
    }

    @Override
    public void onPacket(RawPacket pkt) {
        if (pkt.dir() == Direction.TX) return;
        // Try text extraction first (regex on decoded ASCII text)
        String text = new String(pkt.data(), pkt.offset(), pkt.length());
        var values = extractor.extract(text);
        if (!values.isEmpty()) {
            for (var v : values) textExtracted(v);
            return;
        }
        // Fallback: binary offset extraction (for HEX/binary protocol data)
        // Uses offset rules registered via extractor.addOffsetRule()
        values = extractor.extract(pkt.data());
        for (var v : values) textExtracted(v);
    }

    private void textExtracted(DataExtractor.ExtractedValue v) {
        dataBuffer.addPoint(v.seriesName(), v.value());
    }
}