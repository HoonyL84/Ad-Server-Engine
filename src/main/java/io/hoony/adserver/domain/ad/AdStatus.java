package io.hoony.adserver.domain.ad;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AdStatus {
    ACTIVE("진행"),
    PAUSED("중지"),
    FINISHED("종료");

    private final String description;
}
