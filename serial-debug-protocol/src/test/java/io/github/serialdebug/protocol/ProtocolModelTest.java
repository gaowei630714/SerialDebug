package io.github.serialdebug.protocol;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolModelTest {

    @Test
    void shouldCreateProtocol() {
        Protocol protocol = new Protocol("sensor", "1.0",
                new ProtocolFraming("header", "AA55", 10),
                List.of(new ProtocolField("temp", null, 2, 2, "int16_le", 0.1, 0.0, null, true)));
        assertEquals("sensor", protocol.name());
        assertEquals("1.0", protocol.version());
        assertEquals("header", protocol.framing().mode());
        assertEquals("AA55", protocol.framing().header());
        assertEquals(10, protocol.framing().frameLength());
        assertEquals(1, protocol.fields().size());
    }

    @Test
    void shouldHaveCorrectFieldDefaults() {
        ProtocolField f = new ProtocolField("t", null, 0, 2, "uint16_le", 1.0, 0.0, null, true);
        assertEquals("t", f.name());
        assertNull(f.label());
        assertEquals(0, f.offset());
        assertEquals(2, f.size());
        assertEquals("uint16_le", f.type());
        assertEquals(1.0, f.scale());
        assertEquals(0.0, f.bias());
        assertNull(f.bits());
        assertTrue(f.enabled());
    }

    @Test
    void shouldHaveCorrectFramingFields() {
        ProtocolFraming framing = new ProtocolFraming("header", "AA55", 10);
        assertEquals("header", framing.mode());
        assertEquals("AA55", framing.header());
        assertEquals(10, framing.frameLength());
    }

    @Test
    void shouldHaveNanosTimestamp() {
        long ts = 123456789L;
        ProtocolValue value = new ProtocolValue("x", 1.0, ts);
        assertEquals("x", value.name());
        assertEquals(1.0, value.value(), 0.001);
        assertEquals(ts, value.nanosTimestamp());
    }

    @Test
    void shouldReturnNameAsLabelWhenLabelNull() {
        ProtocolField f = new ProtocolField("myName", null, 0, 1, "uint8", 1.0, 0.0, null, true);
       assertEquals("myName", f.getLabelOrDefault());
    }

    @Test
    void shouldReturnLabelWhenProvided() {
        ProtocolField f = new ProtocolField("myName", "The Label", 0, 1, "uint8", 1.0, 0.0, null, true);
        assertEquals("The Label", f.getLabelOrDefault());
    }
}
