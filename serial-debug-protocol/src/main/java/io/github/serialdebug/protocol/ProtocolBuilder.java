package io.github.serialdebug.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProtocolBuilder {
    private String name;
    private String version;
    private String mode;
    private String header;
    private int frameLength;
    private final List<ProtocolField> fields = new ArrayList<>();

    public ProtocolBuilder name(String n) { this.name = n; return this; }
    public ProtocolBuilder version(String v) { this.version = v; return this; }
    public ProtocolBuilder framing(String mode, String header, int frameLength) {
        this.mode = mode; this.header = header; this.frameLength = frameLength;
        return this;
    }
    public ProtocolBuilder addField(String name, String label, int offset, int size,
                                    String type, double scale, double bias,
                                    List<Integer> bits, boolean enabled) {
        fields.add(new ProtocolField(name, label, offset, size, type, scale, bias, bits, enabled));
        return this;
    }

    public Optional<Protocol> build() {
        if (name == null || name.isBlank()) return Optional.empty();
        Protocol protocol = new Protocol(name, version,
                new ProtocolFraming(mode, header, frameLength), List.copyOf(fields));
        ProtocolValidator.ValidationResult vr = ProtocolValidator.validate(protocol);
        return vr.valid() ? Optional.of(protocol) : Optional.empty();
    }
}
