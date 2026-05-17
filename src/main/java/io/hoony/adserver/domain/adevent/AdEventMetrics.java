package io.hoony.adserver.domain.adevent;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AdEventMetrics {

    private static final String UNKNOWN_SLOT = "unknown";

    private final MeterRegistry meterRegistry;

    public AdEventMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(AdEventType eventType, String slotId, boolean duplicate) {
        String type = eventType.name().toLowerCase();
        String slot = normalizeSlot(slotId);

        meterRegistry.counter("ad.event.received", "type", type, "slot", slot).increment();
        meterRegistry.counter("ad.event.duplicate", "type", type, "slot", slot).increment(duplicate ? 1 : 0);
    }

    public void recordFailure(AdEventType eventType, String slotId) {
        String type = eventType.name().toLowerCase();
        String slot = normalizeSlot(slotId);

        meterRegistry.counter("ad.event.failure", "type", type, "slot", slot).increment();
    }

    private String normalizeSlot(String slotId) {
        if (slotId == null || slotId.isBlank()) {
            return UNKNOWN_SLOT;
        }
        return slotId;
    }
}
