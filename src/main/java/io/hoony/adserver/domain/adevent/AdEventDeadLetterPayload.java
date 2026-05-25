package io.hoony.adserver.domain.adevent;

import java.time.Instant;

public record AdEventDeadLetterPayload(
        String eventId,
        String sourceTopic,
        AdEventType eventType,
        AdEventRequest request,
        String failureReason,
        Instant firstFailedAt
) {
}
