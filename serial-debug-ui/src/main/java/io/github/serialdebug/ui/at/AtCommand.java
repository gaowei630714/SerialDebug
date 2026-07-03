package io.github.serialdebug.ui.at;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An AT command template for the AT companion panel.
 * Java record with Jackson annotations for JSON deserialization.
 */
public record AtCommand(
        @JsonProperty("name") String name,
        @JsonProperty("command") String command,
        @JsonProperty("description") String description) {

    @JsonCreator
    public static AtCommand of(
            @JsonProperty("name") String name,
            @JsonProperty("command") String command,
            @JsonProperty("description") String description) {
        return new AtCommand(
                name == null ? "" : name,
                command == null ? "" : command,
                description == null ? "" : description);
    }
}