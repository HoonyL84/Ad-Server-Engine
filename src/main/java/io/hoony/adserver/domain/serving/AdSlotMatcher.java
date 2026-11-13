package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;

import java.math.BigDecimal;
import java.util.List;

public interface AdSlotMatcher {

    List<AdDocument> match(List<AdDocument> candidates, String slotId);

    default List<AdDocument> match(List<AdDocument> candidates, String slotId, BigDecimal floorPrice) {
        List<AdDocument> slotMatched = match(candidates, slotId);
        if (floorPrice == null || floorPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return slotMatched;
        }
        return slotMatched.stream()
                .filter(ad -> isAboveFloorPrice(ad, floorPrice))
                .toList();
    }

    default boolean isAboveFloorPrice(AdDocument ad, BigDecimal floorPrice) {
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
}
