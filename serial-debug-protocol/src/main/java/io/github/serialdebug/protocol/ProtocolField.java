package io.github.serialdebug.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/** Description of one numeric field within a protocol frame. */
public record ProtocolField(
        String name,
        String label,
        int offset,
        int size,
        String type,
        double scale,
        double bias,
        List<Integer> bits,
        boolean enabled) {

    @JsonIgnore
    public String getLabelOrDefault() {
        return label == null || label.isBlank() ? name : label;
    }
}
