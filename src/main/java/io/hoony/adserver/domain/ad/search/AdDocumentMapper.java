package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.Ad;
import io.hoony.adserver.domain.ad.event.AdEventPayload;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class AdDocumentMapper {

    public AdDocument toDocument(Ad ad) {
        return AdDocument.builder()
                .id(ad.getId())
                .advertiserId(ad.getAdvertiser().getId())
                .title(ad.getTitle())
                .imageUrl(ad.getImageUrl())
                .clickUrl(ad.getClickUrl())
                .maxBid(ad.getMaxBid())
                .totalBudget(ad.getTotalBudget())
                .spentAmount(ad.getSpentAmount())
                .status(ad.getStatus())
                .targetGender(ad.getTargetGender())
                .targetLocationId(ad.getTargetLocationId())
                .interestTags(parseTags(ad.getTargetInterestTags()))
                .targetContext(ad.getTargetContext())
                .build();
    }

    public AdDocument toDocument(AdEventPayload payload) {
        return AdDocument.builder()
                .id(payload.id())
                .advertiserId(payload.advertiserId())
                .title(payload.title())
                .imageUrl(payload.imageUrl())
                .clickUrl(payload.clickUrl())
                .maxBid(payload.maxBid())
                .totalBudget(payload.totalBudget())
                .spentAmount(payload.spentAmount())
                .status(payload.status())
                .targetGender(payload.targetGender())
                .targetLocationId(payload.targetLocationId())
                .interestTags(parseTags(payload.targetInterestTags()))
                .targetContext(payload.targetContext())
                .build();
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }
}
