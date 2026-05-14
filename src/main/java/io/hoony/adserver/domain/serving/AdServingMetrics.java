package io.hoony.adserver.domain.serving;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class AdServingMetrics {

    private static final String UNKNOWN_SLOT = "unknown";

    private final MeterRegistry meterRegistry;

    public AdServingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void record(String slotId, AdServingResult result, Timer.Sample sample) {
        String slot = normalizeSlot(slotId);
        String reason = result.fallbackReason().name();

        meterRegistry.counter("ad.serving.requests", "slot", slot).increment();
        meterRegistry.counter("ad.serving.fallback", "slot", slot, "reason", reason).increment(result.fallback() ? 1 : 0);
        meterRegistry.counter("ad.serving.delivered", "slot", slot).increment(result.selectedAd() == null ? 0 : 1);
        meterRegistry.counter("ad.serving.personalized", "slot", slot).increment(result.fallback() ? 0 : 1);

        DistributionSummary.builder("ad.serving.candidate.count")
                .tag("slot", slot)
                .register(meterRegistry)
                .record(result.candidateCount());

        DistributionSummary.builder("ad.serving.matched.count")
                .tag("slot", slot)
                .register(meterRegistry)
                .record(result.matchedCount());

        sample.stop(Timer.builder("ad.serving.duration")
                .tag("slot", slot)
                .tag("fallback", Boolean.toString(result.fallback()))
                .tag("reason", reason)
                .register(meterRegistry));
    }

    private String normalizeSlot(String slotId) {
        if (slotId == null || slotId.isBlank()) {
            return UNKNOWN_SLOT;
        }
        return slotId;
    }
}
