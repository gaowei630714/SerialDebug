package io.github.serialdebug.core.chart;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts numeric values from serial data strings using regex or fixed-offset rules.
 * Thread-safe for single-writer, single-reader.
 *
 * <p>Two extraction modes:
 * <ul>
 *   <li><b>Named regex</b> — {@code "T:([\\d.]+)\\s+H:([\\d.+)"} → series "T", "H"</li>
 *   <li><b>Fixed offset</b> — extract byte range as integer/float</li>
 * </ul>
 */
public class DataExtractor {

    public record ExtractedValue(String seriesName, double value) {}

    private final List<ExtractionRule> rules = new ArrayList<>();

    /**
     * Add a regex-based extraction rule.
     *
     * @param seriesName display name for this series (e.g. "Temperature")
     * @param regex      pattern with at least one capture group
     * @param groupIndex capture group index (1-based) to extract
     */
    public void addRegexRule(String seriesName, String regex, int groupIndex) {
        rules.add(new ExtractionRule(seriesName, Pattern.compile(regex), groupIndex, -1, -1));
    }

    /**
     * Add a fixed-offset extraction rule (extracts 4 bytes as float from binary data).
     *
     * @param seriesName display name
     * @param byteOffset offset in the byte array
     * @param length     number of bytes (2=int16, 4=int32/float32)
     */
    public void addOffsetRule(String seriesName, int byteOffset, int length) {
        rules.add(new ExtractionRule(seriesName, null, -1, byteOffset, length));
    }

    /**
     * Extract values from an ASCII/hex string.
     *
     * @param text the received data as text
     * @return list of extracted values (may be empty)
     */
    public List<ExtractedValue> extract(String text) {
        List<ExtractedValue> results = new ArrayList<>();
        if (text == null || text.isEmpty()) return results;

        for (ExtractionRule rule : rules) {
            if (rule.pattern() != null) {
                Matcher m = rule.pattern().matcher(text);
                if (m.find()) {
                    try {
                        double val = Double.parseDouble(m.group(rule.groupIndex()));
                        results.add(new ExtractedValue(rule.seriesName(), val));
                    } catch (NumberFormatException | IndexOutOfBoundsException ignored) {}
                }
            }
        }
        return results;
    }

    /**
     * Extract values from raw binary data using offset rules.
     *
     * @param data raw bytes
     * @return list of extracted values
     */
    public List<ExtractedValue> extract(byte[] data) {
        List<ExtractedValue> results = new ArrayList<>();
        if (data == null || data.length == 0) return results;

        for (ExtractionRule rule : rules) {
            if (rule.byteOffset() >= 0) {
                int end = rule.byteOffset() + rule.length();
                if (end <= data.length) {
                    double val = decodeBytes(data, rule.byteOffset(), rule.length());
                    results.add(new ExtractedValue(rule.seriesName(), val));
                }
            }
        }
        return results;
    }

    public List<String> getSeriesNames() {
        return rules.stream().map(ExtractionRule::seriesName).distinct().toList();
    }

    public void clearRules() {
        rules.clear();
    }

    private double decodeBytes(byte[] data, int offset, int length) {
        if (length == 2) {
            return (short) ((data[offset] & 0xFF) | (data[offset + 1] << 8));
        } else if (length == 4) {
            int bits = (data[offset] & 0xFF)
                    | ((data[offset + 1] & 0xFF) << 8)
                    | ((data[offset + 2] & 0xFF) << 16)
                    | (data[offset + 3] << 24);
            return Float.intBitsToFloat(bits);
        }
        return 0;
    }

    private record ExtractionRule(
            String seriesName,
            Pattern pattern,
            int groupIndex,
            int byteOffset,
            int length) {}
}
