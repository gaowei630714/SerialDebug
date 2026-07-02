package io.github.serialdebug.ui.subtab;

import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;

/**
 * M3 placeholder. Full implementation: field extraction -> MetricCard Min/Max/Avg.
 */
public class DashboardConsumer implements PayloadConsumer {
    @Override
    public void onPacket(RawPacket pkt) {
        // M3: extract fields and update metric cards
    }
}
