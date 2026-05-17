package io.hoony.adserver.domain.serving;

public record AdServingResponse(
        String requestId,
        Long adId,
        String title,
        String imageUrl,
        String clickUrl,
        String impressionUrl,
        String clickTrackingUrl,
        boolean fallback,
        String fallbackReason
) {
}
