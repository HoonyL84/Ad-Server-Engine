package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;

public record AdServingResult(
        AdDocument selectedAd,
        boolean fallback,
        ServingFallbackReason fallbackReason,
        int candidateCount,
        int matchedCount
) {

    public AdServingResult(
            AdDocument selectedAd,
            boolean fallback,
            ServingFallbackReason fallbackReason
    ) {
        this(selectedAd, fallback, fallbackReason, 0, 0);
    }
}
