package io.hoony.adserver.domain.ad.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AdUpdatedEvent {
    private final AdEventPayload payload;
}
