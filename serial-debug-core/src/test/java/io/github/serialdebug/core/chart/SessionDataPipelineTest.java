package io.github.serialdebug.core.chart;

import io.github.serialdebug.core.log.Direction;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SessionDataPipelineTest {

    @Test
    void shouldPublishAndDispatch() {
        SessionDataPipeline p = new SessionDataPipeline(16);
        List<String> received = new ArrayList<>();
        p.register(pkt -> received.add(new String(pkt.data(), pkt.offset(), pkt.length())));
        p.publish("hello".getBytes(), 0, 5, Direction.RX);
        p.dispatch();
        assertEquals(List.of("hello"), received);
    }

    @Test
    void shouldNotDispatchToUnregistered() {
        SessionDataPipeline p = new SessionDataPipeline(16);
        List<String> received = new ArrayList<>();
        PayloadConsumer c = pkt -> received.add("should not arrive");
        p.register(c);
        p.unregister(c);
        p.publish("data".getBytes(), 0, 4, Direction.RX);
        p.dispatch();
        assertTrue(received.isEmpty());
    }

    @Test
    void shouldDispatchToMultiple() {
        SessionDataPipeline p = new SessionDataPipeline(16);
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        p.register(pkt -> a.add(new String(pkt.data())));
        p.register(pkt -> b.add(new String(pkt.data())));
        p.publish("X".getBytes(), 0, 1, Direction.RX);
        p.dispatch();
        assertEquals(List.of("X"), a);
        assertEquals(List.of("X"), b);
    }

    @Test
    void clearShouldDiscardPending() {
        SessionDataPipeline p = new SessionDataPipeline(16);
        List<String> received = new ArrayList<>();
        p.register(pkt -> received.add(new String(pkt.data())));
        p.publish("data".getBytes(), 0, 4, Direction.RX);
        p.clear();
        p.dispatch();
        assertTrue(received.isEmpty());
    }

    @Test
    void shouldReturnExactCountAndDrainAllPackets() {
        SessionDataPipeline p = new SessionDataPipeline();
        List<String> received = new ArrayList<>();
        p.register(pkt -> received.add(new String(pkt.data(), pkt.offset(), pkt.length())));

        p.publish("one".getBytes(), 0, 3, Direction.RX);
        p.publish("two".getBytes(), 0, 3, Direction.RX);
        p.publish("three".getBytes(), 0, 5, Direction.RX);

        int drained = p.dispatch();

        assertEquals(3, drained, "dispatch() must return the number of packets drained");
        assertEquals(List.of("one", "two", "three"), received);
        assertTrue(p.isEmpty(), "buffer must be empty after a full drain");
    }

    @Test
    void shouldDrainIncrementallyAcrossMultiplePumps() {
        SessionDataPipeline p = new SessionDataPipeline();
        List<String> received = new ArrayList<>();
        p.register(pkt -> received.add(new String(pkt.data(), pkt.offset(), pkt.length())));

        // First burst
        p.publish("a".getBytes(), 0, 1, Direction.RX);
        p.publish("b".getBytes(), 0, 1, Direction.RX);
        assertEquals(2, p.dispatch());

        // Second burst arrives after the first pump
        p.publish("c".getBytes(), 0, 1, Direction.RX);
        assertEquals(1, p.dispatch());

        assertEquals(List.of("a", "b", "c"), received);
        assertTrue(p.isEmpty());
    }

    @Test
    void shouldPreserveDirection() {
        SessionDataPipeline p = new SessionDataPipeline(16);
        final Direction[] dir = new Direction[1];
        p.register(pkt -> dir[0] = pkt.dir());
        p.publish("X".getBytes(), 0, 1, Direction.TX);
        p.dispatch();
        assertEquals(Direction.TX, dir[0]);
    }
}
