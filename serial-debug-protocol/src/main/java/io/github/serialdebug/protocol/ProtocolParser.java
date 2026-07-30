package io.github.serialdebug.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses framed binary serial data per a Protocol definition.
 * Thread-unsafe - designed to run on the jSerialComm listener thread only.
 */
public class ProtocolParser {

    private static final Logger LOG = Logger.getLogger(ProtocolParser.class.getName());

    private final Protocol protocol;
    private final int frameLength;
    private final boolean headerMode;
    private final byte[] header;
    private final int maxCapacity;
    private final byte[] buffer;
    private int head = 0;
    private int tail = 0;
    private Consumer<ProtocolValue> onValue;

    public ProtocolParser(Protocol protocol) {
        this.protocol = Objects.requireNonNull(protocol, "protocol must not be null");
        ProtocolFraming framing = Objects.requireNonNull(protocol.framing(), "framing must not be null");
        this.frameLength = framing.frameLength();
        this.headerMode = "header".equals(framing.mode());
        this.header = headerMode ? decodeHeaderHex(framing.header()) : null;
        this.maxCapacity = frameLength * 4;
        this.buffer = new byte[maxCapacity];
    }

    public void setOnValue(Consumer<ProtocolValue> onValue) {
        this.onValue = onValue;
    }

    /** Feed new serial data and emit ProtocolValue(s) for each complete frame. */
    public void feed(byte[] data, int offset, int length) {
        while (length-- > 0) {
            if (tail >= buffer.length) {
                discardHead();
            }
            buffer[tail++] = data[offset++];
        }
        processFrames();
    }

    /** Clear the buffer - use when switching protocols. */
    public void clear() {
        head = 0;
        tail = 0;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    // --- frame processing ---

    private void processFrames() {
        while (true) {
            int start;
            if (headerMode) {
                start = findHeader();
                if (start == -1) break;
            } else {
                start = head;
            }
            int available = availableFrom(start);
            if (available < frameLength) break;

            byte[] frame = extract(start, frameLength);
            unpackFrame(frame);
            head = advance(head, frameLength);
        }
    }

    private int findHeader() {
        if (header == null) return -1;
        int searchStart = head;
        int len = tail - head;
        if (len <= 0) return -1;

        for (int i = 0; i <= len - header.length; i++) {
            boolean match = true;
            for (int j = 0; j < header.length; j++) {
                if (buffer[head + i + j] != header[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return head + i;
        }
        return -1;
    }

    private int availableFrom(int start) {
        return tail - start;
    }

    private byte[] extract(int start, int len) {
        byte[] result = new byte[len];
        System.arraycopy(buffer, start, result, 0, len);
        return result;
    }

    private int advance(int pos, int steps) {
        return pos + steps;
    }

    /** Shift buffer by one to make room. */
    private void discardHead() {
        if (head == tail) {
            head = 0;
            tail = 0;
            return;
        }
        // shift [head+1, tail] left by 1
        System.arraycopy(buffer, head + 1, buffer, head, tail - head - 1);
        tail--;
        LOG.log(Level.WARNING, "ProtocolParser: buffer overflow - discarding head byte, possible frame loss");
    }

    // --- field unpacking ---

    private void unpackFrame(byte[] frame) {
        for (ProtocolField field : protocol.fields()) {
            if (!field.enabled()) continue;
            try {
                double raw = readBytes(frame, field);
                if (field.bits() != null) {
                    // Per spec: raw treated as UNSIGNED size*8-bit integer
                    // Cast-to-long would sign-extend negative ints, polluting upper bits.
                    long rawBits = (long) raw & mask(field.size() * 8);
                    raw = bitSlice(rawBits, field.size(), field.bits());
                }
                double value = raw * field.scale() + field.bias();
                if (onValue != null) {
                    onValue.accept(new ProtocolValue(field.name(), value, System.nanoTime()));
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Skipping field " + field.name() + ": " + e.getMessage(), e);
            }
        }
    }

    private double readBytes(byte[] frame, ProtocolField field) {
        int off = field.offset();
        int sz = field.size();
        String type = field.type();
        boolean little = type.endsWith("_le");

        if (sz == 1) {
            if ("uint8".equals(type)) return frame[off] & 0xFF;
            return (double) (byte) frame[off];
        }
        if (sz == 2) {
            int lo = frame[off] & 0xFF, hi = frame[off + 1] & 0xFF;
            int bits = little ? (lo | (hi << 8)) : ((lo << 8) | hi);
            if (type.startsWith("uint")) return bits & 0xFFFF;
            return (short) bits;
        }
        if (sz == 4) {
            int b0 = frame[off] & 0xFF, b1 = frame[off + 1] & 0xFF,
                b2 = frame[off + 2] & 0xFF, b3 = frame[off + 3] & 0xFF;
            int bits = little ? (b0 | (b1 << 8) | (b2 << 16) | (b3 << 24))
                              : ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3);
            if (type.startsWith("float")) return Float.intBitsToFloat(bits);
            return bits;
        }
        // sz == 8, float64
        int b0 = frame[off] & 0xFF, b1 = frame[off + 1] & 0xFF, b2 = frame[off + 2] & 0xFF,
            b3 = frame[off + 3] & 0xFF, b4 = frame[off + 4] & 0xFF, b5 = frame[off + 5] & 0xFF,
            b6 = frame[off + 6] & 0xFF, b7 = frame[off + 7] & 0xFF;
        long lo = little ? ((long)b0 | ((long)b1 << 8) | ((long)b2 << 16) | ((long)b3 << 24))
                         : (((long)b0 << 56) | ((long)b1 << 48) | ((long)b2 << 40) | ((long)b3 << 32));
        long hi = little ? ((long)b4 | ((long)b5 << 8) | ((long)b6 << 16) | ((long)b7 << 24))
                         : (((long)b4 << 56) | ((long)b5 << 48) | ((long)b6 << 40) | ((long)b7 << 32));
        long bits = little ? (hi << 32) | lo : (lo << 32) | hi;
        return Double.longBitsToDouble(bits);
    }

    /** Mask keeping only the lowest n bits (n in [1,64]). */
    private static long mask(int nbits) {
        return nbits == 64 ? -1L : (1L << nbits) - 1;
    }

    private long bitSlice(long raw, int size, List<Integer> bits) {
        long result = 0;
        for (int i = 0; i < bits.size(); i++) {
            int bitIdx = bits.get(i);
            if (((raw >> bitIdx) & 1) != 0) {
                result |= (1L << i);
            }
        }
        return result;
    }

    private static byte[] decodeHeaderHex(String header) {
        byte[] bytes = new byte[header.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(header.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
