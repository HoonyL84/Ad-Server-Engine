package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.user.profile.UserProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultAdMatcher implements AdMatcher {

    @Override
    public List<AdDocument> match(List<AdDocument> candidates, UserProfile profile) {
        if (candidates == null || candidates.isEmpty() || profile == null) {
            return List.of();
        }

        List<AdDocument> matchedAds = new ArrayList<>();
        for (AdDocument ad : candidates) {
            // 1단계: 성별 타게팅 검사
            if (!genderMatches(ad.getTargetGender(), profile.gender())) {
                continue;
            }
            // 2단계: 지역 타게팅 검사
            if (!locationMatches(ad.getTargetLocationId(), profile.locationId())) {
                continue;
            }
            // 3단계: 관심사 태그 교집합 검사
            if (!interestMatches(ad.getInterestTags(), profile.tags())) {
                continue;
            }
            matchedAds.add(ad);
        }
        return List.copyOf(matchedAds);
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
        for (String adTag : adTags) {
            if (adTag == null) {
                continue;
            }
            for (String userTag : userTags) {
                if (adTag.equalsIgnoreCase(userTag)) {
                    return true;
                }
            }
        }
        return false;
    }
}
