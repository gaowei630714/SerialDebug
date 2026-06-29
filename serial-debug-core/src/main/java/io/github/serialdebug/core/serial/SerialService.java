package io.github.serialdebug.core.serial;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public interface SerialService {

    List<SerialPortInfo> listPorts();

    void open(SerialConfig config) throws IOException;

    void close();

    boolean isOpen();

    void sendData(byte[] data) throws IOException;

    void setDataListener(Consumer<byte[]> listener);

    SerialConfig getCurrentConfig();

    long getBytesReceived();

    long getBytesSent();
}