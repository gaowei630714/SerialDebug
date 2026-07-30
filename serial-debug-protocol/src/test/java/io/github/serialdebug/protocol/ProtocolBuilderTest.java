package io.github.serialdebug.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;

class ProtocolBuilderTest {

    @Test
    void shouldBuildValidProtocol() {
        ProtocolBuilder b = new ProtocolBuilder()
                .name("test")
                .version("1.0")
                .framing("header", "AA55", 8)
                .addField("val", "Value", 2, 2, "uint16_le", 0.1, 0.0, null, true);
        Optional<Protocol> result = b.build();
        assertTrue(result.isPresent());
        Protocol p = result.get();
        assertEquals("test", p.name());
        assertEquals(1, p.fields().size());
    }

    @Test
    void shouldRejectEmptyName() {
        ProtocolBuilder b = new ProtocolBuilder()
                .name("")
                .framing("header", "AA55", 8)
                .addField("val", null, 0, 1, "uint8", 1, 0, null, true);
        Optional<Protocol> result = b.build();
        assertFalse(result.isPresent());
    }

    @Test
    void shouldRejectValidatorFailure() {
        ProtocolBuilder b = new ProtocolBuilder()
                .name("bad")
                .framing("header", "AA55", 4)
                .addField("x", null, 0, 10, "uint8", 1, 0, null, true);
        Optional<Protocol> result = b.build();
        assertFalse(result.isPresent());
    }
}
