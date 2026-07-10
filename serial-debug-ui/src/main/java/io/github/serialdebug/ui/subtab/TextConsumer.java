package io.github.serialdebug.ui.subtab;

import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.parser.DataParser;
import io.github.serialdebug.core.parser.HexParser;
import io.github.serialdebug.core.parser.AsciiParser;
import io.github.serialdebug.core.log.Direction;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Consumes raw packets and renders them as HEX/ASCII text with a batched flush.
 *
 * <h2>Batching strategy</h2>
 * Incoming packets are appended to an in-memory {@code StringBuilder} and flushed
 * to the JavaFX {@code TextArea} on the <em>earlier</em> of:
 * <ul>
 *   <li>the flush interval ({@value #FLUSH_INTERVAL_NS} ns ≈ 16 ms, ~60 fps), or</li>
 *   <li>the byte threshold ({@value #FLUSH_SIZE_THRESHOLD} chars).</li>
 * </ul>
 * This coalesces the high-frequency jSerialComm callback into a bounded number of
 * {@code Platform.runLater} calls so the FX thread is not flooded.
 *
 * <p>A flush is logged at DEBUG with the byte size so the log shows the
 * cadence of UI updates — a long gap between flushes while bytes are arriving
 * indicates the dispatch timer is not running.</p>
 */
public class TextConsumer implements PayloadConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(TextConsumer.class);

    private static final long FLUSH_INTERVAL_NS = 16_000_000L;
    private static final int FLUSH_SIZE_THRESHOLD = 4096;

    /** Display timestamps in Beijing time (UTC+8) so all sessions match the user's wall clock. */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(DISPLAY_ZONE);

    private final DataParser hexParser = new HexParser();
    private final DataParser asciiParser = new AsciiParser();
    private final TextArea hexArea;
    private final TextArea asciiArea;
    private final boolean autoScroll;

    private final StringBuilder hexBatch = new StringBuilder();
    private final StringBuilder asciiBatch = new StringBuilder();
    private long lastFlush = System.nanoTime();
    private volatile boolean flushScheduled = false;

    public TextConsumer(TextArea hexArea, TextArea asciiArea, boolean autoScroll) {
        this.hexArea = hexArea;
        this.asciiArea = asciiArea;
        this.autoScroll = autoScroll;
    }

    @Override
    public void onPacket(RawPacket pkt) {
        // Wall-clock millis → Beijing-time string. All sessions share one time base.
        String ts = TS_FORMAT.format(Instant.ofEpochMilli(pkt.epochMillis()));
        hexBatch.append('[').append(ts).append(' ').append(pkt.dir()).append("] ");
        hexBatch.append(hexParser.decode(pkt.data(), pkt.offset(), pkt.length()));
        hexBatch.append('\n');

        asciiBatch.append('[').append(ts).append(' ').append(pkt.dir()).append("] ");
        appendAscii(asciiBatch, pkt);
        asciiBatch.append('\n');

        maybeFlush();
    }

    private void appendAscii(StringBuilder sb, RawPacket pkt) {
        for (int i = pkt.offset(); i < pkt.offset() + pkt.length(); i++) {
            byte b = pkt.data()[i];
            if (b >= 0x20 && b < 0x7F) sb.append((char) b);
            else sb.append('.');
        }
    }

    private void maybeFlush() {
        long now = System.nanoTime();
        if ((now - lastFlush > FLUSH_INTERVAL_NS || hexBatch.length() > FLUSH_SIZE_THRESHOLD)
                && !flushScheduled) {
            flushScheduled = true;
            Platform.runLater(this::flush);
        }
    }

    /**
     * Flush accumulated batches to the TextAreas.
     *
     * <p><strong>Scheduler re-arm bug fix:</strong> {@code flushScheduled} is
     * cleared <em>before</em> the empty-batch check. Previously it lived inside
     * the {@code if (hexBatch.length() > 0)} branch, so if {@code flush()} ever
     * ran on an empty batch (e.g. data arrived between the {@code maybeFlush}
     * check and this call on a quiet line, or a spike of {@code Platform.runLater}
     * coalescing), the flag stayed {@code true} forever and all later data sat in
     * the batch without ever flushing. Clearing it first guarantees every flush
     * re-arms the scheduler for the next interval regardless of batch contents.</p>
     */
    public void flush() {
        flushScheduled = false;
        // Restart the interval timer on every flush attempt so a late/empty flush
        // does not stall the cadence for the packets that follow.
        lastFlush = System.nanoTime();
        if (hexBatch.length() > 0) {
            final String hexText = hexBatch.toString();
            final String asciiText = asciiBatch.toString();
            int byteLen = hexBatch.length();
            hexBatch.setLength(0);
            asciiBatch.setLength(0);

            hexArea.appendText(hexText);
            asciiArea.appendText(asciiText);
            if (autoScroll) {
                hexArea.setScrollTop(Double.MAX_VALUE);
                asciiArea.setScrollTop(Double.MAX_VALUE);
            }
            LOG.debug("flushed {} chars to HEX/ASCII view", byteLen);
        }
    }
}
