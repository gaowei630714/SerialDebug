package io.github.serialdebug.protocol;

/** Frame alignment configuration. mode ∈ {"header","fixed"}. */
public record ProtocolFraming(
        String mode,
        String header,
        int frameLength) {}
