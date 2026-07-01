package io.github.serialdebug.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RateCalculatorTest {

    @Test
    void shouldReturnZeroInitially() {
        RateCalculator calc = new RateCalculator();
        assertEquals(0.0, calc.getRate(), 0.001);
    }

    @Test
    void shouldCalculateRateWithinWindow() {
        RateCalculator calc = new RateCalculator();
        long now = System.nanoTime();
        // Simulate 100 bytes at time T
        calc.addSample(100, now);
        // 100 bytes over 100ms = 1000 B/s
        double rate = calc.getRate(now + 100_000_000L); // 100ms later
        assertEquals(1000.0, rate, 50.0, "Rate should be ~1000 B/s for 100 bytes in 100ms");
    }

    @Test
    void shouldExpireOldSamples() {
        RateCalculator calc = new RateCalculator();
        long now = System.nanoTime();
        calc.addSample(100, now);
        // 2 seconds later — sample expired (window is 1s)
        double rate = calc.getRate(now + 2_000_000_000L);
        assertEquals(0.0, rate, 0.001);
    }

    @Test
    void shouldAccumulateMultipleSamples() {
        RateCalculator calc = new RateCalculator();
        long now = System.nanoTime();
        calc.addSample(50, now);
        calc.addSample(50, now + 100_000_000L); // 100ms later
        double rate = calc.getRate(now + 200_000_000L);
        // 100 bytes over 100ms (time span between first and last sample) = 1000 B/s
        assertEquals(1000.0, rate, 50.0, "Rate should be ~1000 B/s for 100 bytes in 100ms span");
    }
}
