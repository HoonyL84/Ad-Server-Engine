package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DefaultAdSlotMatcher implements AdSlotMatcher {

    @Override
    public List<AdDocument> match(List<AdDocument> candidates, String slotId) {
        return match(candidates, slotId, BigDecimal.ZERO);
    }

    public List<AdDocument> match(List<AdDocument> candidates, String slotId, BigDecimal floorPrice) {
        if (candidates == null || candidates.isEmpty() || slotId == null || slotId.isBlank()) {
            return List.of();
        }

        String normalizedSlotId = slotId.trim().toLowerCase(Locale.ROOT);
        BigDecimal normalizedFloorPrice = floorPrice == null ? BigDecimal.ZERO : floorPrice;

        List<AdDocument> matchedAds = new ArrayList<>();
        for (AdDocument ad : candidates) {
            if (ad == null) {
                continue;
            }
            // 1단계: 요청 지면(slotId) 규격 일치 검사
            if (!supports(ad, normalizedSlotId)) {
                continue;
            }
            // 2단계: SSP 지면 최저 입찰가(Floor Price) 방어선 검사
            if (!isAboveFloorPrice(ad, normalizedFloorPrice)) {
                continue;
            }
            matchedAds.add(ad);
        }
        return List.copyOf(matchedAds);
    }

    public boolean isAboveFloorPrice(AdDocument ad, BigDecimal floorPrice) {
        if (ad == null) {
            return false;
        }
        BigDecimal normalizedFloor = floorPrice == null ? BigDecimal.ZERO : floorPrice;
        if (normalizedFloor.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal maxBid = ad.getMaxBid() == null ? BigDecimal.ZERO : ad.getMaxBid();
        return maxBid.compareTo(normalizedFloor) >= 0;
    }

    private boolean supports(AdDocument ad, String slotId) {
        List<String> slotIds = ad.getSlotIds();
        if (slotIds == null || slotIds.isEmpty()) {
            return false;
        }
        for (String candidate : slotIds) {
            if (candidate == null) {
                continue;
            }
            String normalizedCandidate = candidate.trim().toLowerCase(Locale.ROOT);
            if (AdDocument.ALL_SLOTS.equals(normalizedCandidate) || slotId.equals(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }
}
