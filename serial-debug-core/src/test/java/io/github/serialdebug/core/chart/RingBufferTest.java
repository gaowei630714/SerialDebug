package io.github.serialdebug.core.chart;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingBufferTest {

    @Test
    void shouldOfferAndPoll() {
        RingBuffer<String> buf = new RingBuffer<>(4);
        buf.offer("a");
        buf.offer("b");
        assertEquals("a", buf.poll());
        assertEquals("b", buf.poll());
        assertNull(buf.poll());
    }

    @Test
    void shouldOverwriteOldestWhenFull() {
        RingBuffer<String> buf = new RingBuffer<>(2);
        buf.offer("a");
        buf.offer("b");
        buf.offer("c");
        assertEquals("b", buf.poll());
        assertEquals("c", buf.poll());
        assertNull(buf.poll());
    }

    @Test
    void shouldReturnCorrectSize() {
        RingBuffer<String> buf = new RingBuffer<>(4);
        assertEquals(0, buf.size());
        buf.offer("a");
        buf.offer("b");
        assertEquals(2, buf.size());
        buf.poll();
        assertEquals(1, buf.size());
    }

    @Test
    void shouldClear() {
        RingBuffer<String> buf = new RingBuffer<>(4);
        buf.offer("a");
        buf.offer("b");
        buf.clear();
        assertEquals(0, buf.size());
        assertNull(buf.poll());
    }

    @Test
    void shouldReportCapacity() {
        RingBuffer<String> buf = new RingBuffer<>(8);
        assertEquals(8, buf.capacity());
    }

    @Test
    void shouldHandleEmptyPoll() {
        RingBuffer<String> buf = new RingBuffer<>(4);
        assertNull(buf.poll());
    }
}
