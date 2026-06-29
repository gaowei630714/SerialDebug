package io.github.serialdebug.core.serial;

import java.util.Objects;

public class SerialConfig {

    public static final int[] SUPPORTED_BAUD_RATES = {
            300, 600, 1200, 2400, 4800, 9600, 19200, 38400,
            57600, 115200, 230400, 460800, 921600
    };

    public enum Parity {
        NONE, EVEN, ODD, MARK, SPACE
    }

    public enum FlowControl {
        NONE, RTS_CTS, XON_XOFF
    }

    private String portName = "";
    private int baudRate = 115200;
    private int dataBits = 8;
    private int stopBits = 1;
    private Parity parity = Parity.NONE;
    private FlowControl flowControl = FlowControl.NONE;

    public String getPortName() { return portName; }

    public void setPortName(String portName) {
        this.portName = Objects.requireNonNullElse(portName, "");
    }

    public int getBaudRate() { return baudRate; }

    public void setBaudRate(int baudRate) {
        if (baudRate <= 0) {
            throw new IllegalArgumentException("Baud rate must be positive: " + baudRate);
        }
        this.baudRate = baudRate;
    }

    public int getDataBits() { return dataBits; }

    public void setDataBits(int dataBits) {
        if (dataBits < 5 || dataBits > 8) {
            throw new IllegalArgumentException("Data bits must be 5-8: " + dataBits);
        }
        this.dataBits = dataBits;
    }

    public int getStopBits() { return stopBits; }

    public void setStopBits(int stopBits) {
        if (stopBits < 1 || stopBits > 2) {
            throw new IllegalArgumentException("Stop bits must be 1-2: " + stopBits);
        }
        this.stopBits = stopBits;
    }

    public Parity getParity() { return parity; }

    public void setParity(Parity parity) {
        this.parity = Objects.requireNonNullElse(parity, Parity.NONE);
    }

    public FlowControl getFlowControl() { return flowControl; }

    public void setFlowControl(FlowControl flowControl) {
        this.flowControl = Objects.requireNonNullElse(flowControl, FlowControl.NONE);
    }

    @Override
    public String toString() {
        return portName + " " + baudRate + " " + dataBits + paritySymbol() + stopBits;
    }

    private String paritySymbol() {
        return switch (parity) {
            case NONE -> "N";
            case EVEN -> "E";
            case ODD -> "O";
            case MARK -> "M";
            case SPACE -> "S";
        };
    }
}