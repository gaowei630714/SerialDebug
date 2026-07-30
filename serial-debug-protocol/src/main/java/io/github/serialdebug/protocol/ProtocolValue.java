package io.github.serialdebug.protocol;

/** A single extracted numeric value from one parsed protocol field. */
public record ProtocolValue(
        String name,
        double value,
        long nanosTimestamp) {}
