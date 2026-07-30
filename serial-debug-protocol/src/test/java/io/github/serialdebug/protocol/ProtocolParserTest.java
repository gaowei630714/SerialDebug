package io.github.serialdebug.protocol;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolParserTest {

    private static Protocol headerProto() {
        return new Protocol("test", "1.0",
                new ProtocolFraming("header", "AA55", 8),
                List.of(new ProtocolField("val", null, 2, 2, "uint16_le", 1.0, 0.0, null, true)));
    }

    private static Protocol fixedProto() {
        return new Protocol("fixed", "1.0",
                new ProtocolFraming("fixed", "", 4),
                List.of(new ProtocolField("v", null, 0, 2, "uint16_le", 1.0, 0.0, null, true)));
    }

    @Test
    void shouldExtractSingleFrame() {
        ProtocolParser p = new ProtocolParser(headerProto());
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        byte[] frame = {(byte) 0xAA, (byte) 0x55, 0x27, 0x10, 0, 0, 0, 0};
        p.feed(frame, 0, frame.length);
        assertEquals(1, out.size());
        assertEquals("val", out.get(0).name());
        assertEquals(4135.0, out.get(0).value(), 0.001);
    }

    @Test
    void shouldExtractTwoAdjacentFrames() {
        ProtocolParser p = new ProtocolParser(headerProto());
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        byte[] data = {(byte) 0xAA, (byte) 0x55, 0x01, 0x00, 0, 0, 0, 0,
                       (byte) 0xAA, (byte) 0x55, 0x02, 0x00, 0, 0, 0, 0};
        p.feed(data, 0, data.length);
        assertEquals(2, out.size());
        assertEquals(1.0, out.get(0).value(), 0.001);
        assertEquals(2.0, out.get(1).value(), 0.001);
    }

    @Test
    void shouldReassembleAcrossTwoFeeds() {
        ProtocolParser p = new ProtocolParser(headerProto());
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        p.feed(new byte[]{(byte) 0xAA, (byte) 0x55, 0x07, 0x10}, 0, 4);
        assertEquals(0, out.size());
        p.feed(new byte[]{0, 0, 0, 0}, 0, 4);
        assertEquals(1, out.size());
        assertEquals(4103.0, out.get(0).value(), 0.001);
    }

    @Test
    void shouldSkipGarbageBeforeHeader() {
        ProtocolParser p = new ProtocolParser(headerProto());
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        byte[] data = {(byte) 0xFF, 0x00, (byte) 0xDE, (byte) 0xAA, (byte) 0x55, 0x03, 0x00, 0, 0, 0, 0};
        p.feed(data, 0, data.length);
        assertEquals(1, out.size());
        assertEquals(3.0, out.get(0).value(), 0.001);
    }

    @Test
    void shouldNotEmitIncompleteFrame() {
        ProtocolParser p = new ProtocolParser(headerProto());
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        p.feed(new byte[]{(byte) 0xAA, (byte) 0x55, 0x01}, 0, 3);
        assertEquals(0, out.size());
    }

    @Test
    void shouldSliceByFixedLength() {
        ProtocolParser p = new ProtocolParser(fixedProto());
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        p.feed(new byte[]{0x0A, 0x00, 0, 0}, 0, 4);
        assertEquals(1, out.size());
        assertEquals(10.0, out.get(0).value(), 0.001);
    }

    @Test
    void shouldExtractInt16Le() {
        ProtocolParser p = new ProtocolParser(new Protocol("x", "1",
                new ProtocolFraming("header", "AA55", 6),
                List.of(new ProtocolField("v", null, 2, 2, "int16_le", 1, 0, null, true))));
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        byte[] frame = {(byte) 0xAA, (byte) 0x55, (byte) 0xE7, (byte) 0xFF, 0, 0};
        p.feed(frame, 0, frame.length);
        assertEquals(-25.0, out.get(0).value(), 0.001);
    }

    @Test
    void shouldApplyScaleAndBias() {
        ProtocolParser p = new ProtocolParser(new Protocol("x", "1",
                new ProtocolFraming("header", "AA55", 6),
                List.of(new ProtocolField("v", null, 2, 2, "uint16_le", 0.1, 20.0, null, true))));
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        // raw = 0x0064 = 100, value = 100*0.1 + 20 = 30
        byte[] frame = {(byte) 0xAA, (byte) 0x55, 0x64, 0x00, 0, 0};
        p.feed(frame, 0, frame.length);
        assertEquals(30.0, out.get(0).value(), 0.001);
    }

    @Test
    void shouldExtractBitSlice() {
        ProtocolParser p = new ProtocolParser(new Protocol("x", "1",
                new ProtocolFraming("header", "AA55", 4),
                List.of(new ProtocolField("b0", null, 2, 1, "uint8", 1, 0, List.of(0), true),
                        new ProtocolField("b23", null, 2, 1, "uint8", 1, 0, List.of(2, 3), true))));
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        // byte = 0b1101_0011 -> bit 0 = 1, bits 2,3 = 00 -> 0
        byte[] frame = {(byte) 0xAA, (byte) 0x55, (byte) 0xD3, 0};
        p.feed(frame, 0, frame.length);
        assertEquals(2, out.size());
        assertEquals(1.0, out.get(0).value(), 0.001);
        assertEquals(0.0, out.get(1).value(), 0.001);
    }

    @Test
    void shouldSkipDisabledField() {
        ProtocolParser p = new ProtocolParser(new Protocol("x", "1",
                new ProtocolFraming("header", "AA55", 6),
                List.of(new ProtocolField("a", null, 2, 2, "uint16_le", 1, 0, null, true),
                        new ProtocolField("b", null, 4, 2, "uint16_le", 1, 0, null, false))));
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        p.feed(new byte[]{(byte) 0xAA, (byte) 0x55, 0x01, 0, (byte) 0x02, 0}, 0, 6);
        assertEquals(1, out.size());
        assertEquals("a", out.get(0).name());
    }

    @Test
    void shouldClearBuffer() {
        ProtocolParser p = new ProtocolParser(headerProto());
        List<ProtocolValue> out = new ArrayList<>();
        p.setOnValue(out::add);
        p.feed(new byte[]{(byte) 0xAA, (byte) 0x55, 0x01}, 0, 3);
        p.clear();
        // After clear, incomplete frame is gone
        p.feed(new byte[]{0x00, 0, 0, 0, 0}, 0, 5);
        assertEquals(0, out.size());
    }
}
