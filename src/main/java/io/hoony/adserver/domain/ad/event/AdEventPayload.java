package io.hoony.adserver.domain.ad.event;

import io.hoony.adserver.domain.ad.Ad;
import io.hoony.adserver.domain.ad.AdStatus;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.Map;

public record AdEventPayload(
        Long id,
        Long advertiserId,
        String title,
        String imageUrl,
        String clickUrl,
        BigDecimal maxBid,
        BigDecimal totalBudget,
        BigDecimal spentAmount,
        AdStatus status,
        String targetGender,
        String targetLocationId,
        String targetInterestTags,
        Map<String, Object> targetContext,
        Map<String, String> traceContext
) {
    public static AdEventPayload from(Ad ad) {
        return new AdEventPayload(
                ad.getId(),
                ad.getAdvertiser().getId(),
                ad.getTitle(),
                ad.getImageUrl(),
                ad.getClickUrl(),
                ad.getMaxBid(),
                ad.getTotalBudget(),
                ad.getSpentAmount(),
                ad.getStatus(),
                ad.getTargetGender(),
                ad.getTargetLocationId(),
                ad.getTargetInterestTags(),
                ad.getTargetContext(),
                MDC.getCopyOfContextMap()
        );
    }
}
