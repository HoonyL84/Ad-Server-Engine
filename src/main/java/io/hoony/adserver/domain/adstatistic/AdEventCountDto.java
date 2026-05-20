package io.hoony.adserver.domain.adstatistic;

import io.hoony.adserver.domain.adevent.AdEventType;

public record AdEventCountDto(
        Long adId,
        AdEventType eventType,
        Long count
) {
}
