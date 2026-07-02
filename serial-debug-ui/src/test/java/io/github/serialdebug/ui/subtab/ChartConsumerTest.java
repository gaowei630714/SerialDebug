package io.github.serialdebug.ui.subtab;

import io.github.serialdebug.core.chart.ChartDataBuffer;
import io.github.serialdebug.core.chart.DataExtractor;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.log.Direction;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChartConsumerTest {

    @Test
    void shouldExtractTextValues() {
        ChartDataBuffer buf = new ChartDataBuffer();
        DataExtractor ext = new DataExtractor();
        ext.addRegexRule("T", "T:(\\d+)", 1);
        ChartConsumer consumer = new ChartConsumer(buf, ext);

        consumer.onPacket(new RawPacket("T:25".getBytes(), 0, 4, 0, Direction.RX));

        List<ChartDataBuffer.DataPoint> points = buf.getSeries("T");
        assertEquals(1, points.size());
        assertEquals(25.0, points.get(0).value(), 0.001);
    }

    @Test
    void shouldSkipTxPackets() {
        ChartDataBuffer buf = new ChartDataBuffer();
        DataExtractor ext = new DataExtractor();
        ext.addRegexRule("V", "V=(\\d+)", 1);
        ChartConsumer consumer = new ChartConsumer(buf, ext);

        consumer.onPacket(new RawPacket("V=5".getBytes(), 0, 3, 0, Direction.TX));
        assertTrue(buf.isEmpty());
    }

    @Test
    void shouldHandleNoMatch() {
        ChartDataBuffer buf = new ChartDataBuffer();
        DataExtractor ext = new DataExtractor();
        ext.addRegexRule("X", "X:(\\d+)", 1);
        ChartConsumer consumer = new ChartConsumer(buf, ext);

        consumer.onPacket(new RawPacket("no match".getBytes(), 0, 8, 0, Direction.RX));
        assertTrue(buf.isEmpty());
    }

    @Test
    void shouldExtractMultipleValues() {
        ChartDataBuffer buf = new ChartDataBuffer();
        DataExtractor ext = new DataExtractor();
        ext.addRegexRule("T", "T:(\\d+)", 1);
        ext.addRegexRule("H", "H:(\\d+)", 1);
        ChartConsumer consumer = new ChartConsumer(buf, ext);

        consumer.onPacket(new RawPacket("T:25 H:60".getBytes(), 0, 9, 0, Direction.RX));

        assertEquals(1, buf.getSeries("T").size());
        assertEquals(1, buf.getSeries("H").size());
    }

    @Test
    void shouldFallbackToBinaryExtraction() {
        ChartDataBuffer buf = new ChartDataBuffer();
        DataExtractor ext = new DataExtractor();
        ext.addOffsetRule("Val", 0, 2); // int16 at offset 0
        ChartConsumer consumer = new ChartConsumer(buf, ext);

        // Raw binary: 0x00 0x05 = int16 5
        consumer.onPacket(new RawPacket(new byte[]{0x00, 0x05}, 0, 2, 0, Direction.RX));

        List<ChartDataBuffer.DataPoint> points = buf.getSeries("Val");
        assertEquals(1, points.size());
    }
}