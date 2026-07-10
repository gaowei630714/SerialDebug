package io.github.serialdebug.core.chart;

import io.github.serialdebug.core.log.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-session data pipeline. Receives raw bytes from the serial port
 * (on the jSerialComm listener thread), wraps each chunk into a {@link RawPacket},
 * stores it in a ring buffer, and later dispatches it to registered consumers.
 *
 * <h2>Critical architectural note — dispatch is NOT automatic</h2>
 * {@link #publish} (producer side) and {@link #dispatch} (consumer side) run on
 * <em>different</em> threads and are only coupled by the ring buffer. Nothing
 * here calls {@code dispatch()}; the current sole caller is the AnimationTimer
 * inside {@code SessionTabContent.startWaveform()} — i.e. dispatch only runs
 * while the user has the <em>waveform chart tab</em> active.
 *
 * <p>If that timer is not running (no chart tab open, or the tab was never
 * activated) the buffer fills to capacity ({@value #DEFAULT_CAPACITY} packets)
 * and <strong>{@code publish} silently overwrites the oldest packet</strong>.
 * From the user's view the RX display then looks "frozen" even though bytes
 * are still arriving at the OS level. This is a known design limitation, not
 * a serial-driver fault. (A future watchdog / dispatch-on-publish would
 * remove the dependency on the chart tab.)</p>
 *
 * <p>When the buffer overflows a WARN is emitted once per overflow so the log
 * shows the gap without flooding on a sustained overload.</p>
 */
public class SessionDataPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(SessionDataPipeline.class);

    /**
     * One received/sent chunk.
     *
     * <p>{@code epochMillis} is <strong>wall-clock milliseconds since the Unix
     * epoch</strong> (from {@link System#currentTimeMillis}), captured on the
     * jSerialComm listener thread at publish time. It is <em>not</em> a
     * {@link System#nanoTime nanoTime} value — nanoTime is monotonic but has no
     * relation to wall time, so it cannot be formatted as a human-readable clock.
     *
     * <p>Using wall-clock millis means every session in the same JVM shares one
     * time base, and the UI can render it in any zone (currently Asia/Shanghai,
     * see {@link io.github.serialdebug.ui.subtab.TextConsumer}).</p>
     */
    public record RawPacket(
            byte[] data, int offset, int length,
            long epochMillis, Direction dir) {}

    private static final int DEFAULT_CAPACITY = 8192;
    private final RingBuffer<RawPacket> buffer;
    private final List<PayloadConsumer> consumers = new CopyOnWriteArrayList<>();

    /**
     * Set true for one cycle after an overflow so the WARN is emitted once per
     * continuous overflow rather than once per dropped packet (which would flood
     * the log on a sustained overload).
     */
    private boolean overflowLatched = false;

    public SessionDataPipeline() { this(DEFAULT_CAPACITY); }

    public SessionDataPipeline(int capacity) {
        this.buffer = new RingBuffer<>(capacity);
    }

    /**
     * Called by the jSerialComm listener thread. Copies data for safety so the
     * native buffer can be reused. If the ring buffer is already full the oldest
     * packet is silently overwritten — a WARN is logged so the event is visible.
     */
    public void publish(byte[] data, int offset, int length, Direction dir) {
        byte[] copy = Arrays.copyOfRange(data, offset, offset + length);

        // Detect overflow: offer() returns void, so compare size before/after.
        int sizeBefore = buffer.size();
        // Wall-clock millis (not nanoTime): every session shares one time base
        // and the UI can render it as a real clock (Beijing time). nanoTime is
        // only suitable for measuring elapsed durations, never for timestamps.
        buffer.offer(new RawPacket(copy, 0, copy.length, System.currentTimeMillis(), dir));
        if (sizeBefore == buffer.capacity()) {
            // Buffer was full before this offer → one packet got overwritten.
            if (!overflowLatched) {
                LOG.warn("ring buffer FULL (capacity={}) — oldest packet overwritten. "
                                + "dispatch() is likely not running "
                                + "(waveform chart tab inactive?).",
                        buffer.capacity());
                overflowLatched = true;
            }
        } else {
            overflowLatched = false;
        }
    }

    public void register(PayloadConsumer consumer) { consumers.add(consumer); }
    public void unregister(PayloadConsumer consumer) { consumers.remove(consumer); }

    /**
     * Drain the ring buffer and dispatch every packet to all registered consumers.
     *
     * <p>This is the <em>only</em> consumer-side entry point; it must be driven by
     * an external timer/thread (currently SessionTabContent.startWaveform).</p>
     *
     * @return number of packets dispatched (useful for diagnostics)
     */
    public int dispatch() {
        int count = 0;
        RawPacket pkt;
        while ((pkt = buffer.poll()) != null) {
            for (PayloadConsumer c : consumers) c.onPacket(pkt);
            count++;
        }
        if (count > 0) {
            LOG.debug("dispatched {} packet(s) to {} consumer(s)", count, consumers.size());
        }
        return count;
    }

    public void clear() { buffer.clear(); }
    public boolean isEmpty() { return buffer.isEmpty(); }
    public int size() { return buffer.size(); }
    public int consumerCount() { return consumers.size(); }
}
