package io.hoony.adserver.domain.openrtb;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * OpenRTB 입찰 및 Win/Loss 알림 메트릭 기록기.
 */
@Component
public class OpenRtbMetrics {

    private static final String UNKNOWN_SLOT = "unknown";
    private static final String UNKNOWN_REASON = "unknown";

    private final MeterRegistry meterRegistry;

    public OpenRtbMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordBid(String slotId, OpenRtbBidResult result, Timer.Sample sample) {
        String slot = normalize(slotId, UNKNOWN_SLOT);
        String outcome = result.hasBid() ? "bid" : "no_bid";
        String reason = result.noBidReason().name();

        meterRegistry.counter("openrtb.bid.requests", "slot", slot).increment();
        meterRegistry.counter("openrtb.bid.responses", "slot", slot, "outcome", outcome, "reason", reason).increment();

        sample.stop(Timer.builder("openrtb.bid.duration")
                .tag("slot", slot)
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(meterRegistry));
    }

    public void recordWin(String slotId) {
        meterRegistry.counter("openrtb.notice.events", "type", "win", "slot", normalize(slotId, UNKNOWN_SLOT))
                .increment();
    }

    public void recordLoss(String slotId, String reason) {
        meterRegistry.counter(
                        "openrtb.notice.events",
                        "type", "loss",
                        "slot", normalize(slotId, UNKNOWN_SLOT),
                        "reason", normalize(reason, UNKNOWN_REASON))
                .increment();
    }

    private String normalize(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
