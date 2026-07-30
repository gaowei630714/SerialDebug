package io.github.serialdebug.protocol;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolValidatorTest {

    private static Protocol build(String name, String mode, String header,
                                  int frameLen, String fName, int off, int size) {
        return build(name, mode, header, frameLen,
                List.of(new ProtocolField(fName, null, off, size, "uint8", 1.0, 0.0, null, true)));
    }

    private static Protocol build(String name, String mode, String header,
                                  int frameLen, List<ProtocolField> fields) {
        return new Protocol(name, "1.0", new ProtocolFraming(mode, header, frameLen), fields);
    }

    @Test
    void shouldAcceptValidProtocol() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("s", "header", "AA55", 10, "x", 0, 1));
        assertTrue(r.valid());
    }

    @Test
    void shouldRejectEmptyName() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("", "header", "AA55", 10, "x", 0, 1));
        assertFalse(r.valid());
    }

    @Test
    void shouldRejectNegativeFrameLength() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("s", "header", "AA55", -1, "x", 0, 1));
        assertFalse(r.valid());
    }

    @Test
    void shouldRejectInvalidFramingMode() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("s", "invalid", "AA55", 10, "x", 0, 1));
        assertFalse(r.valid());
    }

    @Test
    void shouldRejectMissingHeaderInHeaderMode() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("s", "header", "", 10, "x", 0, 1));
        assertFalse(r.valid());
    }

    @Test
    void shouldRejectOddLengthHeader() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("s", "header", "AA5", 10, "x", 0, 1));
        assertFalse(r.valid());
    }

    @Test
    void shouldRejectFieldOverFrameLength() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("s", "header", "AA55", 4, "x", 2, 4));
        assertFalse(r.valid());
    }

    @Test
    void shouldRejectDuplicateFieldNames() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("s", "header", "AA55", 10,
                        List.of(
                                new ProtocolField("x", null, 0, 1, "uint8", 1, 0, null, true),
                                new ProtocolField("x", null, 1, 1, "uint8", 1, 0, null, true))));
        assertFalse(r.valid());
    }

    @Test
    void shouldRejectInvalidType() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("s", "header", "AA55", 10,
                        List.of(new ProtocolField("x", null, 0, 1, "invalid", 1, 0, null, true))));
        assertFalse(r.valid());
    }

    @Test
    void shouldRejectOutOfRangeBitIndex() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("s", "header", "AA55", 10,
                        List.of(new ProtocolField("x", null, 0, 1, "uint8", 1, 0, List.of(8), true))));
        assertFalse(r.valid());
    }

    @Test
    void shouldAcceptFixedModeWithoutHeader() {
        ProtocolValidator.ValidationResult r =
                ProtocolValidator.validate(build("s", "fixed", "", 10, "x", 0, 1));
        assertTrue(r.valid());
    }
}
