package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;

public record AdServingResult(
        AdDocument selectedAd,
        boolean fallback,
        ServingFallbackReason fallbackReason
) {
}

