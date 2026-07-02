package io.github.serialdebug.ui.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Immutable snapshot of a serial port connection configuration.
 */
public record PortHistory(
        @JsonProperty("portName") String portName,
        @JsonProperty("baudRate") int baudRate,
        @JsonProperty("dataBits") int dataBits,
        @JsonProperty("stopBits") int stopBits,
        @JsonProperty("parity") String parity,
        @JsonProperty("flowControl") String flowControl,
        @JsonProperty("lastConnected") long lastConnected) {

    @JsonCreator
    public PortHistory {}
}
