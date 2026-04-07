package io.hoony.adserver.domain.advertiser;

import lombok.Getter;

@Getter
public enum AdvertiserStatus {
    ACTIVE("정상"),
    INACTIVE("정지");

    private final String description;

    AdvertiserStatus(String description) {
        this.description = description;
    }
}
