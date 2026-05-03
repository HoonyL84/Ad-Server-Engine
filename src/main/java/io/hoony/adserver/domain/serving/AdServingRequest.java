package io.hoony.adserver.domain.serving;

import jakarta.validation.constraints.NotBlank;

public record AdServingRequest(
        @NotBlank String userId,
        @NotBlank String slotId
) {
}

