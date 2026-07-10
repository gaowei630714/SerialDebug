package io.github.serialdebug.core.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * jSerialComm-backed implementation of {@link SerialService}.
 *
 * <h2>Receive path (critical — is the source of the "RX frozen" failure)</h2>
 * <pre>
 *   jSerialComm native listener thread
 *     ─► serialEvent()                       [this file; MUST NOT throw]
 *           ─► bytesReceived  += len          [volatile, feeds status bar label]
 *           ──► dataListener.accept(data)      [set in SerialSession ctor]
 *                 ─► pipeline.publish()        [copies bytes into RingBuffer]
 *                       ─► (drained by SessionTabContent.startWaveform AnimationTimer
 *                            OR DisplayController, depending on active code path)
 * </pre>
 *
 * <h2>Known failure mode — listener thread death</h2>
 * jSerialComm delivers {@code serialEvent} on a <em>single</em> internal thread.
 * If that callback throws (even from downstream code, e.g. the
 * {@code dataListener} / pipeline), jSerialComm swallows the exception and the
 * <strong>thread silently stops delivering events</strong>. After that:
 * <ul>
 *   <li>the OS still sees the port open ({@link SerialPort#isOpen()} == true),</li>
 *   <li>the status-bar RX-byte counter stops advancing (freeze),</li>
 *   <li>the only recovery is to close + reopen (or restart).</li>
 * </ul>
 * The root cause is <em>below</em> this layer (USB-serial adapter / driver
 * transient I/O error — counters freeze even with no data flowing); the fix in
 * <em>this</em> class is purely defensive: keep the listener thread alive so we
 * at least see the next event, and log every anomaly for evidence collection.
 * Application-level auto-reconnect belongs in a watchdog, not here.
 *
 * <p>All lifecycle and per-chunk events are logged at DEBUG; anomalies at WARN
 * or ERROR. File log writes to {@code ~/.serialdebug/logs/} (see logback.xml).</p>
 */
public class JSerialCommService implements SerialService {

    /** Logger for the serial I/O layer; routed to ~/.serialdebug/logs at DEBUG. */
    private static final Logger LOG = LoggerFactory.getLogger(JSerialCommService.class);

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
        LOG.debug("listed {} port(s)", result.size());
        return result;
    }

    @Override
    public void open(SerialConfig config) throws IOException {
        if (isOpen()) {
            throw new IOException("Serial port is already open");
        }

        LOG.info("opening port {} @ {} {}{}{}{} flow={}",
                config.getPortName(), config.getBaudRate(),
                config.getDataBits(),
                config.getParity().name().charAt(0),
                config.getStopBits(),
                config.getFlowControl());

        SerialPort port = SerialPort.getCommPort(config.getPortName());
        port.setBaudRate(config.getBaudRate());
        port.setNumDataBits(config.getDataBits());
        port.setNumStopBits(config.getStopBits());
        port.setParity(toJSerialCommParity(config.getParity()));
        port.setFlowControl(toJSerialCommFlowControl(config.getFlowControl()));

        if (!port.openPort()) {
            throw new IOException("Failed to open port: " + config.getPortName());
        }

        // Read semi-blocking with 100 ms timeout: the callback fires at most every
        // 100 ms when idle — enough to notice a stalled listener via the log gap.
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
        LOG.debug("port {} opened; set TIMEOUT_READ_SEMI_BLOCKING readTimeout=100 writeTimeout=0",
                config.getPortName());

        // Attach the data listener only if one is already registered. SerialSession
        // wires it in via setDataListener() during construction (before open()).
        if (dataListener != null) {
            addListenerToPort(port);
            LOG.debug("data listener attached to {}", config.getPortName());
        } else {
            LOG.warn("opening {} with NO data listener attached — port will open but "
                    + "received bytes will never be dispatched", config.getPortName());
        }

        this.activePort = port;
        this.currentConfig = config;
        this.bytesReceived = 0;
        this.bytesSent = 0;
    }

    @Override
    public void close() {
        if (activePort != null) {
            String portName = activePort.getSystemPortName();
            try {
                activePort.removeDataListener();
                activePort.closePort();
                LOG.info("closed port {} (rxBytes={} txBytes={})",
                        portName, bytesReceived, bytesSent);
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
        LOG.debug("sent {} bytes on {} (total txBytes={})",
                written, activePort.getSystemPortName(), bytesSent);
    }

    @Override
    public void setDataListener(Consumer<byte[]> listener) {
        Consumer<byte[]> previous = this.dataListener;
        this.dataListener = listener;
        LOG.debug("data listener {}→{}",
                previous == null ? "null" : "set",
                listener == null ? "null" : "set");

        // If the port is already open, swap the live listener. removeDataListener()
        // is idempotent and jSerialComm tolerates re-registering.
        if (activePort != null && activePort.isOpen()) {
            activePort.removeDataListener();
            if (listener != null) {
                addListenerToPort(activePort);
                LOG.debug("reattached data listener to live port {}",
                        activePort.getSystemPortName());
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

    /**
     * Registers the jSerialComm data listener.
     *
     * <p><strong>Contract:</strong> {@code serialEvent} MUST NOT throw — see the
     * class Javadoc (listener-thread-death failure mode). Every statement that
     * could propagate an exception from downstream code is wrapped; anomalies are
     * logged at ERROR so they land in the file log for post-mortem.</p>
     */
    private void addListenerToPort(SerialPort port) {
        port.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                // Defensive: isolate this callback so a throw cannot silently kill
                // the jSerialComm listener thread (see class Javadoc).
                try {
                    if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
                        LOG.debug("ignored serial event type={}", event.getEventType());
                        return;
                    }

                    byte[] data = event.getReceivedData();
                    bytesReceived += data.length;
                    LOG.debug("RX {} bytes on {} (total rxBytes={})",
                            data.length, port.getSystemPortName(), bytesReceived);

                    Consumer<byte[]> listener = dataListener;
                    if (listener == null) {
                        LOG.warn("received {} bytes on {} but dataListener is NULL — "
                                        + "bytes counted but never dispatched",
                                data.length, port.getSystemPortName());
                        return;
                    }
                    listener.accept(data);
                } catch (Throwable t) {
                    // Catch Throwable (not Exception) — even Errors must not escape.
                    LOG.error("unhandled exception in serialEvent callback on {} — "
                                    + "if this repeats the listener thread is dead; "
                                    + "close + reopen to recover",
                            port.getSystemPortName(), t);
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
