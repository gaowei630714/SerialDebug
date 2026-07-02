package io.github.serialdebug.core.chart;

/**
 * Consumes {@link SessionDataPipeline.RawPacket} data published by a pipeline.
 */
public interface PayloadConsumer {
    void onPacket(SessionDataPipeline.RawPacket packet);
}
