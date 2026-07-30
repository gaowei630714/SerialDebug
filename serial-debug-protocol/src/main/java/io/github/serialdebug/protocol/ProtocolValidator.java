package io.github.serialdebug.protocol;

import java.util.HexFormat;
import java.util.Set;

public final class ProtocolValidator {

    public record ValidationResult(boolean valid, String errorMessage) {}

    private static final Set<String> VALID_TYPES = Set.of(
            "uint8",
            "uint16_le", "uint16_be",
            "uint32_le", "uint32_be",
            "int8",
            "int16_le", "int16_be",
            "int32_le", "int32_be",
            "float32_le", "float32_be",
            "float64_le", "float64_be"
    );

    private ProtocolValidator() {}

    public static ValidationResult validate(Protocol protocol) {
        if (protocol.name() == null || protocol.name().isBlank()) {
            return invalid("Protocol name must not be empty");
        }
        ProtocolFraming f = protocol.framing();
        if (f.frameLength() <= 0) {
            return invalid("frameLength must be positive");
        }
        String mode = f.mode();
        if (!"header".equals(mode) && !"fixed".equals(mode)) {
            return invalid("framing mode must be 'header' or 'fixed'");
        }
        if ("header".equals(mode)) {
            String header = f.header();
            if (header == null || header.isBlank()) {
                return invalid("header is required when framing mode is 'header'");
            }
            if (header.length() % 2 != 0) {
                return invalid("header hex string must have even length");
            }
            if (header.length() < 2 || header.length() > 16) {
                return invalid("header must be 1-8 bytes (2-16 hex chars)");
            }
            try {
                HexFormat.of().parseHex(header);
            } catch (Exception e) {
                return invalid("header must contain valid hex characters: " + e.getMessage());
            }
        }
        if (protocol.fields().isEmpty()) {
            return invalid("protocol must have at least one field");
        }
        return validateFields(protocol.fields(), f.frameLength());
    }

    private static ValidationResult validateFields(Iterable<ProtocolField> fields, int frameLength) {
        Set<String> seen = new java.util.HashSet<>();
        for (ProtocolField field : fields) {
            if (field.name() == null || field.name().isBlank()) {
                return invalid("field name must not be empty");
            }
            if (!seen.add(field.name())) {
                return invalid("duplicate field name: " + field.name());
            }
            if (field.offset() < 0) {
                return invalid("field '" + field.name() + "' offset must be non-negative");
            }
            if (field.size() < 1) {
                return invalid("field '" + field.name() + "' size must be at least 1");
            }
            if (field.offset() + field.size() > frameLength) {
                return invalid("field '" + field.name() + "' at offset " + field.offset()
                        + " + size " + field.size() + " exceeds frameLength " + frameLength);
            }
            if (!VALID_TYPES.contains(field.type())) {
                return invalid("field '" + field.name() + "' has unknown type '" + field.type() + "'");
            }
            if (field.bits() != null) {
                int maxBit = field.size() * 8;
                for (int b : field.bits()) {
                    if (b < 0 || b >= maxBit) {
                        return invalid("field '" + field.name() + "' bit index " + b
                                + " out of range [0," + (maxBit - 1) + "]");
                    }
                }
            }
        }
        return new ValidationResult(true, null);
    }

    private static ValidationResult invalid(String msg) {
        return new ValidationResult(false, msg);
    }
}
