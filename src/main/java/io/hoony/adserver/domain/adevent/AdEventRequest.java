package io.hoony.adserver.domain.adevent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdEventRequest(
        @NotBlank String eventId,
        @NotBlank String requestId,
        @NotNull Long adId,
        @NotBlank String userId,
        @NotBlank String slotId,
        String landingUrl
) {
}
