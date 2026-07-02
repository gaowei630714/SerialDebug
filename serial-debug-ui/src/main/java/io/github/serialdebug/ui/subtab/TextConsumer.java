package io.github.serialdebug.ui.subtab;

import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;
import io.github.serialdebug.core.parser.DataParser;
import io.github.serialdebug.core.parser.HexParser;
import io.github.serialdebug.core.parser.AsciiParser;
import io.github.serialdebug.core.log.Direction;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

/**
 * Consumes raw packets and renders as HEX/ASCII text with batched flush.
 */
public class TextConsumer implements PayloadConsumer {

    private static final long FLUSH_INTERVAL_NS = 16_000_000L;
    private static final int FLUSH_SIZE_THRESHOLD = 4096;

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
        String ts = formatTimestamp(pkt.nanosTimestamp());
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

    public void flush() {
        if (hexBatch.length() > 0) {
            final String hexText = hexBatch.toString();
            final String asciiText = asciiBatch.toString();
            hexBatch.setLength(0);
            asciiBatch.setLength(0);
            lastFlush = System.nanoTime();
            flushScheduled = false;

            hexArea.appendText(hexText);
            asciiArea.appendText(asciiText);
            if (autoScroll) {
                hexArea.setScrollTop(Double.MAX_VALUE);
                asciiArea.setScrollTop(Double.MAX_VALUE);
            }
        }
    }

    private String formatTimestamp(long nanos) {
        long ms = nanos / 1_000_000;
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        return String.format("%02d:%02d:%02d.%03d", h % 24, m % 60, s % 60, ms % 1000);
    }
}
