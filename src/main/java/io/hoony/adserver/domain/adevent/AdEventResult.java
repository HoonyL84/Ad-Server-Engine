package io.hoony.adserver.domain.adevent;

public record AdEventResult(
        String eventId,
        AdEventType eventType,
        boolean duplicate
) {
}
