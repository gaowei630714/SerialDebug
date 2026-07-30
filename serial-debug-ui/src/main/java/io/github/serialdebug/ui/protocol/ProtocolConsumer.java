package io.github.serialdebug.ui.protocol;

import io.github.serialdebug.core.chart.ChartDataBuffer;
import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.log.Direction;
import io.github.serialdebug.protocol.Protocol;
import io.github.serialdebug.protocol.ProtocolParser;
import io.github.serialdebug.protocol.ProtocolValue;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Consumes raw packets from the pipeline, feeds them to a ProtocolParser,
 * and pushes extracted values into ChartDataBuffer + calls onExtracted.
 */
public class ProtocolConsumer implements PayloadConsumer {

    private final ChartDataBuffer dataBuffer;
    private final Consumer<List<ProtocolValue>> onExtracted;
    private volatile ProtocolParser parser;

    public ProtocolConsumer(ChartDataBuffer dataBuffer,
                            Consumer<List<ProtocolValue>> onExtracted) {
        this.dataBuffer = dataBuffer;
        this.onExtracted = onExtracted;
        this.parser = null;
    }

    /** Switch to a new protocol (or null to disable). Old parser is cleared. */
    public void setProtocol(Protocol protocol) {
        if (protocol == null) {
            if (parser != null) parser.clear();
            this.parser = null;
        } else {
            this.parser = new ProtocolParser(protocol);
        }
    }

    @Override
    public void onPacket(RawPacket pkt) {
        if (pkt.dir() == Direction.TX) return;
        if (parser == null) return;

        List<ProtocolValue> batch = new ArrayList<>();
        parser.setOnValue(v -> {
            dataBuffer.addPoint(v.name(), v.value());
            batch.add(v);
        });
        parser.feed(pkt.data(), pkt.offset(), pkt.length());

        if (!batch.isEmpty() && onExtracted != null) {
            List<ProtocolValue> copy = List.copyOf(batch);
            Platform.runLater(() -> onExtracted.accept(copy));
        }
    }

    public void clear() {
        if (parser != null) parser.clear();
    }

    /**
     * Get the current parser. May be null if no protocol is selected.
     *
     * @return the current parser, or null. The parser is thread-unsafe;
     * do not call feed/clear/setOnValue from outside the listener thread.
     */
    public ProtocolParser getParser() {
        return parser;
    }
}
