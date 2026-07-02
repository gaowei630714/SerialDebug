package io.github.serialdebug.ui.subtab;

import io.github.serialdebug.core.chart.ChartDataBuffer;
import io.github.serialdebug.core.chart.DataExtractor;
import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.log.Direction;

/**
 * Consumes raw packets and adds extracted numeric values to ChartDataBuffer.
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
        String text = new String(pkt.data(), pkt.offset(), pkt.length());
        var values = extractor.extract(text);
        for (var v : values) {
            dataBuffer.addPoint(v.seriesName(), pkt.nanosTimestamp(), v.value());
        }
    }
}
