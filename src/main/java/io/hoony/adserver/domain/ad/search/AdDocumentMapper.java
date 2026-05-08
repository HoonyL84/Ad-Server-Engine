package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.Ad;
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
                .status(ad.getStatus())
                .targetGender(ad.getTargetGender())
                .targetLocationId(ad.getTargetLocationId())
                .interestTags(parseTags(ad.getTargetInterestTags()))
                .targetContext(ad.getTargetContext())
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
