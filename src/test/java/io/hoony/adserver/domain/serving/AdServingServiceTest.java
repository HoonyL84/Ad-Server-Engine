package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.user.profile.UserProfile;
import io.hoony.adserver.domain.user.profile.UserProfileClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdServingServiceTest {

    private final UserProfileClient userProfileClient = mock(UserProfileClient.class);
    private final AdCandidateSearchService candidateSearchService = mock(AdCandidateSearchService.class);

    @Test
    @DisplayName("프로필과 후보가 정상일 때 타겟 광고를 반환한다.")
    void servesTargetedAdWhenProfileIsAvailable() {
        AdServingService service = new AdServingService(userProfileClient, candidateSearchService, 30);
        UserProfile profile = new UserProfile("1", "M", "1:11", 29, List.of("fashion"));
        AdDocument targeted = ad(1L, "M", "1:11", List.of("fashion"), "3000");
        AdDocument broad = ad(2L, "ALL", "0", List.of("finance"), "5000");

        when(userProfileClient.getUserProfile("1")).thenReturn(Optional.of(profile));
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(broad, targeted));

        AdServingResult result = service.serve("1", "home");

        assertThat(result.fallback()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo(ServingFallbackReason.NONE);
        assertThat(result.selectedAd().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("프로필이 없으면 기본 후보로 fallback 한다.")
    void fallsBackWhenProfileIsMissing() {
        AdServingService service = new AdServingService(userProfileClient, candidateSearchService, 30);
        AdDocument broad = ad(2L, "ALL", "0", List.of("finance"), "5000");

        when(userProfileClient.getUserProfile("missing")).thenReturn(Optional.empty());
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(broad));

        AdServingResult result = service.serve("missing", "home");

        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(ServingFallbackReason.PROFILE_NOT_FOUND);
        assertThat(result.selectedAd().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("프로필은 있지만 타겟에 맞는 후보가 없으면 기본 후보로 fallback 한다.")
    void fallsBackWhenTargetDoesNotMatch() {
        AdServingService service = new AdServingService(userProfileClient, candidateSearchService, 30);
        UserProfile profile = new UserProfile("1", "M", "1:11", 29, List.of("fashion"));
        AdDocument broad = ad(2L, "F", "2:21", List.of("interior"), "5000");

        when(userProfileClient.getUserProfile("1")).thenReturn(Optional.of(profile));
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(broad));

        AdServingResult result = service.serve("1", "home");

        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(ServingFallbackReason.TARGET_NOT_MATCHED);
        assertThat(result.selectedAd().getId()).isEqualTo(2L);
    }

    private AdDocument ad(Long id, String gender, String locationId, List<String> tags, String bid) {
        return AdDocument.builder()
                .id(id)
                .advertiserId(1L)
                .title("ad-" + id)
                .maxBid(new BigDecimal(bid))
                .status(AdStatus.ACTIVE)
                .targetGender(gender)
                .targetLocationId(locationId)
                .interestTags(tags)
                .build();
    }
}
