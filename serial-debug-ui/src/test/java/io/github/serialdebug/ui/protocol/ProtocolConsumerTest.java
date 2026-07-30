package io.github.serialdebug.ui.protocol;

import io.github.serialdebug.core.chart.ChartDataBuffer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.log.Direction;
import io.github.serialdebug.protocol.Protocol;
import io.github.serialdebug.protocol.ProtocolFraming;
import io.github.serialdebug.protocol.ProtocolField;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

class ProtocolConsumerTest {

    private static Protocol tempProto() {
        return new Protocol("temp", "1.0",
                new ProtocolFraming("header", "AA55", 8),
                List.of(
                        new ProtocolField("temp", "Temperature", 2, 2, "int16_le", 0.1, 0.0, null, true),
                        new ProtocolField("humid", "Humidity", 4, 2, "uint16_le", 1.0, 0.0, null, true)));
    }

    private static RawPacket rxPacket(byte[] bytes) {
        return new RawPacket(bytes, 0, bytes.length, 0L, Direction.RX);
    }

    private static RawPacket txPacket(byte[] bytes) {
        return new RawPacket(bytes, 0, bytes.length, System.nanoTime(), Direction.TX);
    }

    @Test
    void shouldEmitExtractedValuesToBuffer() {
        ChartDataBuffer buffer = new ChartDataBuffer();
        ProtocolConsumer consumer = new ProtocolConsumer(buffer, null);
        consumer.setProtocol(tempProto());

        // AA55 + temp=0x1027 (4135*0.1=413.5) + humid=0x0064 (100)
        byte[] data = {(byte) 0xAA, (byte) 0x55, (byte) 0x27, (byte) 0x10, 0x64, 0x00, 0, 0};
        consumer.onPacket(rxPacket(data));

        assertEquals(2, buffer.getSeriesNames().size());
        List<ChartDataBuffer.DataPoint> temp = buffer.getSeries("temp");
        List<ChartDataBuffer.DataPoint> humid = buffer.getSeries("humid");
        assertEquals(1, temp.size());
        assertEquals(413.5, temp.get(0).value(), 0.001);
        assertEquals(1, humid.size());
       assertEquals(100.0, humid.get(0).value(), 0.001);
    }

    @Test
    void shouldSkipTxPackets() {
        ChartDataBuffer buffer = new ChartDataBuffer();
        ProtocolConsumer consumer = new ProtocolConsumer(buffer, null);
        consumer.setProtocol(tempProto());

        byte[] data = {(byte) 0xAA, (byte) 0x55, 0x01, 0, 0x02, 0, 0, 0};
        consumer.onPacket(txPacket(data));

        assertTrue(buffer.isEmpty());
    }

    @Test
    void shouldAccumulateMultipleFieldsPerFrame() {
        ChartDataBuffer buffer = new ChartDataBuffer();
        List<List<Object>> extracted = new ArrayList<>();
        ProtocolConsumer consumer = new ProtocolConsumer(buffer, v -> extracted.add((List) v));

        consumer.setProtocol(tempProto());
        byte[] data = {(byte) 0xAA, (byte) 0x55, 0x00, 0x01, 0x00, 0x02, 0, 0};
        consumer.onPacket(rxPacket(data));

        assertEquals(2, buffer.getSeriesNames().size());
        assertEquals(25.6, buffer.getSeries("temp").get(0).value(), 0.001);
        assertEquals(512.0, buffer.getSeries("humid").get(0).value(), 0.001);
    }
}
