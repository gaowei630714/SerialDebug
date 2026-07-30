package io.github.serialdebug.protocol;

import java.util.List;

/** JSON-declarative description of one binary protocol. */
public record Protocol(
        String name,
        String version,
        ProtocolFraming framing,
        List<ProtocolField> fields) {}
