package io.github.serialdebug.ui.subtab;

import io.github.serialdebug.core.chart.PayloadConsumer;
import io.github.serialdebug.core.chart.SessionDataPipeline.RawPacket;

/**
 * M3 placeholder. Full implementation: AT command matching, response comparison, PASS/FAIL.
 */
public class AtConsumer implements PayloadConsumer {
    @Override
    public void onPacket(RawPacket pkt) {
        // M3: match outgoing AT commands and incoming responses
    }
}
