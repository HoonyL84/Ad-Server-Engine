package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class DefaultAdSlotMatcher implements AdSlotMatcher {

    @Override
    public List<AdDocument> match(List<AdDocument> candidates, String slotId) {
        if (candidates == null || candidates.isEmpty() || slotId == null || slotId.isBlank()) {
            return List.of();
        }

        String normalizedSlotId = slotId.trim().toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(ad -> supports(ad, normalizedSlotId))
                .toList();
    }

    private boolean supports(AdDocument ad, String slotId) {
        List<String> slotIds = ad.getSlotIds();
        if (slotIds == null || slotIds.isEmpty()) {
            return false;
        }
        return slotIds.stream()
                .filter(candidate -> candidate != null)
                .map(candidate -> candidate.trim().toLowerCase(Locale.ROOT))
                .anyMatch(candidate -> AdDocument.ALL_SLOTS.equals(candidate) || slotId.equals(candidate));
    }
}
