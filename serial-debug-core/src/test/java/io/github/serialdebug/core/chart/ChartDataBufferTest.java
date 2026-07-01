package io.github.serialdebug.core.chart;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChartDataBufferTest {

    @Test
    void shouldAddAndRetrievePoints() {
        ChartDataBuffer buffer = new ChartDataBuffer(100);
        buffer.addPoint("series1", 1.0);
        buffer.addPoint("series1", 2.0);
        buffer.addPoint("series2", 10.0);

        List<ChartDataBuffer.DataPoint> s1 = buffer.getSeries("series1");
        assertEquals(2, s1.size());
        assertEquals(1.0, s1.get(0).value(), 0.001);
        assertEquals(2.0, s1.get(1).value(), 0.001);

        List<ChartDataBuffer.DataPoint> s2 = buffer.getSeries("series2");
        assertEquals(1, s2.size());
        assertEquals(10.0, s2.get(0).value(), 0.001);
    }

    @Test
    void shouldEnforceCapacity() {
        ChartDataBuffer buffer = new ChartDataBuffer(3);
        buffer.addPoint("s", 1.0);
        buffer.addPoint("s", 2.0);
        buffer.addPoint("s", 3.0);
        buffer.addPoint("s", 4.0); // should evict 1.0

        List<ChartDataBuffer.DataPoint> series = buffer.getSeries("s");
        assertEquals(3, series.size());
        assertEquals(2.0, series.get(0).value(), 0.001);
        assertEquals(4.0, series.get(2).value(), 0.001);
    }

    @Test
    void shouldReportMinMax() {
        ChartDataBuffer buffer = new ChartDataBuffer();
        buffer.addPoint("s", -5.0);
        buffer.addPoint("s", 10.0);
        buffer.addPoint("s", 3.0);

        double[] minMax = buffer.getMinMax();
        assertEquals(-5.0, minMax[0], 0.001);
        assertEquals(10.0, minMax[1], 0.001);
    }

    @Test
    void shouldReturnDefaultMinMaxWhenEmpty() {
        ChartDataBuffer buffer = new ChartDataBuffer();
        double[] minMax = buffer.getMinMax();
        assertEquals(0.0, minMax[0], 0.001);
        assertEquals(1.0, minMax[1], 0.001);
    }

    @Test
    void shouldClearAllData() {
        ChartDataBuffer buffer = new ChartDataBuffer();
        buffer.addPoint("s", 1.0);
        buffer.clear();
        assertTrue(buffer.getSeries("s").isEmpty());
        assertTrue(buffer.isEmpty());
    }

    @Test
    void shouldTrackMultipleSeries() {
        ChartDataBuffer buffer = new ChartDataBuffer();
        buffer.addPoint("temp", 25.0);
        buffer.addPoint("humidity", 60.0);

        List<String> names = buffer.getSeriesNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("temp"));
        assertTrue(names.contains("humidity"));
    }
}
