package io.hoony.adserver.domain.ad.event;

import io.hoony.adserver.domain.ad.Ad;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AdCreatedEvent {
    private final Ad ad;
}
