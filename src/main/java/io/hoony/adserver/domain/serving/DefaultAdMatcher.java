package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.user.profile.UserProfile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultAdMatcher implements AdMatcher {

    @Override
    public List<AdDocument> match(List<AdDocument> candidates, UserProfile profile) {
        return candidates.stream()
                .filter(ad -> genderMatches(ad.getTargetGender(), profile.gender()))
                .filter(ad -> locationMatches(ad.getTargetLocationId(), profile.locationId()))
                .filter(ad -> interestMatches(ad.getInterestTags(), profile.tags()))
                .toList();
    }

    private boolean genderMatches(String adGender, String userGender) {
        if (adGender == null || adGender.isBlank() || "ALL".equalsIgnoreCase(adGender)) {
            return true;
        }
        return adGender.equalsIgnoreCase(userGender);
    }

    private boolean locationMatches(String adLocation, String userLocation) {
        if (adLocation == null || adLocation.isBlank() || "0".equals(adLocation)) {
            return true;
        }
        return adLocation.equals(userLocation);
    }

    private boolean interestMatches(List<String> adTags, List<String> userTags) {
        if (adTags == null || adTags.isEmpty()) {
            return true;
        }
        if (userTags == null || userTags.isEmpty()) {
            return false;
        }
        return adTags.stream()
                .anyMatch(tag -> userTags.stream()
                        .anyMatch(userTag -> userTag.equalsIgnoreCase(tag)));
    }
}
