package io.github.serialdebug.core.chart;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DataExtractorTest {

    @Test
    void shouldExtractSingleRegexGroup() {
        DataExtractor extractor = new DataExtractor();
        extractor.addRegexRule("Temperature", "T:(\\d+)", 1);

        List<DataExtractor.ExtractedValue> result = extractor.extract("T:25");
        assertEquals(1, result.size());
        assertEquals("Temperature", result.get(0).seriesName());
        assertEquals(25.0, result.get(0).value(), 0.001);
    }

    @Test
    void shouldExtractMultipleRegexGroups() {
        DataExtractor extractor = new DataExtractor();
        extractor.addRegexRule("Temp", "T:(\\d+)", 1);
        extractor.addRegexRule("Humidity", "H:(\\d+)", 1);

        List<DataExtractor.ExtractedValue> result = extractor.extract("T:25 H:60");
        assertEquals(2, result.size());
        assertEquals("Temp", result.get(0).seriesName());
        assertEquals(25.0, result.get(0).value(), 0.001);
        assertEquals("Humidity", result.get(1).seriesName());
        assertEquals(60.0, result.get(1).value(), 0.001);
    }

    @Test
    void shouldExtractDecimalValues() {
        DataExtractor extractor = new DataExtractor();
        extractor.addRegexRule("Voltage", "V:(\\d+\\.\\d+)", 1);

        List<DataExtractor.ExtractedValue> result = extractor.extract("V:3.3");
        assertEquals(1, result.size());
        assertEquals(3.3, result.get(0).value(), 0.001);
    }

    @Test
    void shouldReturnEmptyForNoMatch() {
        DataExtractor extractor = new DataExtractor();
        extractor.addRegexRule("Temp", "T:(\\d+)", 1);

        List<DataExtractor.ExtractedValue> result = extractor.extract("no match");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullInput() {
        DataExtractor extractor = new DataExtractor();
        extractor.addRegexRule("Temp", "T:(\\d+)", 1);
        assertTrue(extractor.extract((String) null).isEmpty());
    }

    @Test
    void shouldGetSeriesNames() {
        DataExtractor extractor = new DataExtractor();
        extractor.addRegexRule("T", "T:(\\d+)", 1);
        extractor.addRegexRule("H", "H:(\\d+)", 1);

        List<String> names = extractor.getSeriesNames();
        assertTrue(names.contains("T"));
        assertTrue(names.contains("H"));
    }
}
