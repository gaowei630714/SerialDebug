package io.github.serialdebug.core.chart;

import io.github.serialdebug.core.log.Direction;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-session data pipeline. Receives raw bytes from serial port,
 * wraps into RawPacket, dispatches to registered consumers.
 */
public class SessionDataPipeline {

    public record RawPacket(
            byte[] data, int offset, int length,
            long nanosTimestamp, Direction dir) {}

    private static final int DEFAULT_CAPACITY = 8192;
    private final RingBuffer<RawPacket> buffer;
    private final List<PayloadConsumer> consumers = new CopyOnWriteArrayList<>();

    public SessionDataPipeline() { this(DEFAULT_CAPACITY); }

    public SessionDataPipeline(int capacity) {
        this.buffer = new RingBuffer<>(capacity);
    }

    /** Called by jSerialComm listener thread. Copies data for safety. */
    public void publish(byte[] data, int offset, int length, Direction dir) {
        byte[] copy = Arrays.copyOfRange(data, offset, offset + length);
        buffer.offer(new RawPacket(copy, 0, copy.length, System.nanoTime(), dir));
    }

    public void register(PayloadConsumer consumer) { consumers.add(consumer); }
    public void unregister(PayloadConsumer consumer) { consumers.remove(consumer); }

    /** Drain buffer and dispatch to all registered consumers. */
    public void dispatch() {
        RawPacket pkt;
        while ((pkt = buffer.poll()) != null) {
            for (PayloadConsumer c : consumers) c.onPacket(pkt);
        }
    }

    public void clear() { buffer.clear(); }
    public boolean isEmpty() { return buffer.isEmpty(); }
    public int size() { return buffer.size(); }
    public int consumerCount() { return consumers.size(); }
}
