package io.github.serialdebug.core.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class JSerialCommService implements SerialService {

    private SerialPort activePort;
    private SerialConfig currentConfig = new SerialConfig();
    private Consumer<byte[]> dataListener;
    private volatile long bytesReceived;
    private volatile long bytesSent;

    @Override
    public List<SerialPortInfo> listPorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        List<SerialPortInfo> result = new ArrayList<>(ports.length);
        for (SerialPort port : ports) {
            result.add(new SerialPortInfo(
                    port.getSystemPortName(),
                    port.getDescriptivePortName()));
        }
        return result;
    }

    @Override
    public void open(SerialConfig config) throws IOException {
        if (isOpen()) {
            throw new IOException("Serial port is already open");
        }

        SerialPort port = SerialPort.getCommPort(config.getPortName());
        port.setBaudRate(config.getBaudRate());
        port.setNumDataBits(config.getDataBits());
        port.setNumStopBits(config.getStopBits());
        port.setParity(toJSerialCommParity(config.getParity()));
        port.setFlowControl(toJSerialCommFlowControl(config.getFlowControl()));

        if (!port.openPort()) {
            throw new IOException("Failed to open port: " + config.getPortName());
        }

        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

        if (dataListener != null) {
            addListenerToPort(port);
        }

        this.activePort = port;
        this.currentConfig = config;
        this.bytesReceived = 0;
        this.bytesSent = 0;
    }

    @Override
    public void close() {
        if (activePort != null) {
            try {
                activePort.removeDataListener();
                activePort.closePort();
            } finally {
                activePort = null;
                currentConfig = new SerialConfig();
            }
        }
    }

    @Override
    public boolean isOpen() {
        return activePort != null && activePort.isOpen();
    }

    @Override
    public void sendData(byte[] data) throws IOException {
        if (!isOpen()) {
            throw new IOException("Serial port is not open");
        }
        if (data == null || data.length == 0) {
            return;
        }
        int written = activePort.writeBytes(data, data.length);
        if (written != data.length) {
            throw new IOException(
                    "Failed to send all bytes: wrote " + written + " of " + data.length);
        }
        bytesSent += written;
    }

    @Override
    public void setDataListener(Consumer<byte[]> listener) {
        this.dataListener = listener;
        if (activePort != null && activePort.isOpen()) {
            activePort.removeDataListener();
            if (listener != null) {
                addListenerToPort(activePort);
            }
        }
    }

    @Override
    public SerialConfig getCurrentConfig() {
        return currentConfig;
    }

    @Override
    public long getBytesReceived() {
        return bytesReceived;
    }

    @Override
    public long getBytesSent() {
        return bytesSent;
    }

    private void addListenerToPort(SerialPort port) {
        port.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
                    byte[] data = event.getReceivedData();
                    bytesReceived += data.length;
                    Consumer<byte[]> listener = dataListener;
                    if (listener != null) {
                        listener.accept(data);
                    }
                }
            }
        });
    }

    private int toJSerialCommParity(SerialConfig.Parity parity) {
        return switch (parity) {
            case NONE -> SerialPort.NO_PARITY;
            case EVEN -> SerialPort.EVEN_PARITY;
            case ODD -> SerialPort.ODD_PARITY;
            case MARK -> SerialPort.MARK_PARITY;
            case SPACE -> SerialPort.SPACE_PARITY;
        };
    }

    private int toJSerialCommFlowControl(SerialConfig.FlowControl flowControl) {
        return switch (flowControl) {
            case NONE -> SerialPort.FLOW_CONTROL_DISABLED;
            case RTS_CTS ->
                    SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED;
            case XON_XOFF ->
                    SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED;
        };
    }
}