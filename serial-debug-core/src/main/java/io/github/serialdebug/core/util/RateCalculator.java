package io.github.serialdebug.core.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sliding-window byte rate calculator.
 * Tracks (timestamp, bytes) samples and computes bytes/second
 * over the most recent 1-second window. Thread-safe.
 */
public class RateCalculator {

    private static final long WINDOW_NANOS = 1_000_000_000L; // 1 second

    private record Sample(long timestampNanos, long bytes) {}

    private final Deque<Sample> samples = new ArrayDeque<>();
    private long totalBytesInWindow;

    /**
     * Record a new data point. Call this on each receive/send.
     */
    public synchronized void addSample(long bytes) {
        addSample(bytes, System.nanoTime());
    }

    /** Package-visible for testing. */
    synchronized void addSample(long bytes, long timestampNanos) {
        samples.addLast(new Sample(timestampNanos, bytes));
        totalBytesInWindow += bytes;
        prune(timestampNanos);
    }

    /**
     * Get the rate in bytes/second over the last 1-second window.
     */
    public synchronized double getRate() {
        return getRate(System.nanoTime());
    }

    /** Package-visible for testing. */
    synchronized double getRate(long nowNanos) {
        prune(nowNanos);
        if (samples.isEmpty()) return 0.0;
        Sample last = samples.getLast();
        // If the newest sample is outside the 1-second window, data is stale
        if (last.timestampNanos() < nowNanos - WINDOW_NANOS) return 0.0;
        if (samples.size() == 1) {
            // Single sample: rate = bytes / time elapsed since that sample
            Sample s = samples.getFirst();
            long elapsed = nowNanos - s.timestampNanos();
            if (elapsed <= 0) return 0.0;
            return (double) s.bytes() / (elapsed / 1_000_000_000.0);
        }
        Sample first = samples.getFirst();
        long elapsedNanos = last.timestampNanos() - first.timestampNanos();
        if (elapsedNanos <= 0) return 0.0;
        return (double) totalBytesInWindow / (elapsedNanos / 1_000_000_000.0);
    }

    /** Reset all samples and counter. */
    public synchronized void reset() {
        samples.clear();
        totalBytesInWindow = 0;
    }

    private synchronized void prune(long nowNanos) {
        long cutoff = nowNanos - WINDOW_NANOS;
        while (samples.size() > 1 && samples.getFirst().timestampNanos() < cutoff) {
            Sample removed = samples.removeFirst();
            totalBytesInWindow -= removed.bytes();
        }
    }
}
