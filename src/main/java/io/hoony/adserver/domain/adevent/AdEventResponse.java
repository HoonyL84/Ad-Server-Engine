package io.hoony.adserver.domain.adevent;

public record AdEventResponse(
        String eventId,
        String eventType,
        boolean duplicate
) {
}
