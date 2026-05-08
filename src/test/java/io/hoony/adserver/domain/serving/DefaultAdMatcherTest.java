package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.user.profile.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAdMatcherTest {

    private final DefaultAdMatcher matcher = new DefaultAdMatcher();

    @Test
    @DisplayName("성별, 지역, 관심사가 모두 맞는 후보만 반환한다.")
    void matchesCandidateWhenAllTargetsMatch() {
        UserProfile profile = new UserProfile("u1", "M", "1:11", 30, List.of("fashion", "sports"));
        AdDocument matched = ad(1L, "M", "1:11", List.of("fashion"));
        AdDocument genderMismatch = ad(2L, "F", "1:11", List.of("fashion"));
        AdDocument locationMismatch = ad(3L, "M", "2:21", List.of("fashion"));
        AdDocument interestMismatch = ad(4L, "M", "1:11", List.of("interior"));

        List<AdDocument> result = matcher.match(
                List.of(matched, genderMismatch, locationMismatch, interestMismatch),
                profile
        );

        assertThat(result).extracting(AdDocument::getId).containsExactly(1L);
    }

    @Test
    @DisplayName("ALL 성별, 0 지역, 빈 관심사는 전체 타겟으로 처리한다.")
    void matchesBroadTargetCandidate() {
        UserProfile profile = new UserProfile("u1", "F", "9:99", 30, List.of("unknown"));
        AdDocument broad = ad(1L, "ALL", "0", List.of());

        List<AdDocument> result = matcher.match(List.of(broad), profile);

        assertThat(result).extracting(AdDocument::getId).containsExactly(1L);
    }

    @Test
    @DisplayName("관심사 태그는 대소문자를 구분하지 않는다.")
    void matchesInterestIgnoringCase() {
        UserProfile profile = new UserProfile("u1", "M", "1:11", 30, List.of("Fashion"));
        AdDocument candidate = ad(1L, "M", "1:11", List.of("fashion"));

        List<AdDocument> result = matcher.match(List.of(candidate), profile);

        assertThat(result).extracting(AdDocument::getId).containsExactly(1L);
    }

    private AdDocument ad(Long id, String gender, String locationId, List<String> tags) {
        return AdDocument.builder()
                .id(id)
                .advertiserId(1L)
                .title("ad-" + id)
                .maxBid(new BigDecimal("1000"))
                .status(AdStatus.ACTIVE)
                .targetGender(gender)
                .targetLocationId(locationId)
                .interestTags(tags)
                .build();
    }
}
