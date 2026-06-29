package io.github.serialdebug.core.serial;

import java.util.Objects;

public class SerialPortInfo {

    private final String systemPortName;
    private final String description;

    public SerialPortInfo(String systemPortName, String description) {
        this.systemPortName = Objects.requireNonNull(systemPortName, "portName");
        this.description = Objects.requireNonNullElse(description, "");
    }

    public String getSystemPortName() {
        return systemPortName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description.isEmpty() ? systemPortName : systemPortName + " - " + description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SerialPortInfo that)) return false;
        return systemPortName.equals(that.systemPortName);
    }

    @Override
    public int hashCode() {
        return systemPortName.hashCode();
    }
}
