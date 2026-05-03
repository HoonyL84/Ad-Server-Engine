package io.hoony.adserver.domain.serving;

public record AdServingResponse(
        Long adId,
        String title,
        String imageUrl,
        String clickUrl,
        boolean fallback,
        String fallbackReason
) {
}
