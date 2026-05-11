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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdServingServiceTest {

    private final UserProfileClient userProfileClient = mock(UserProfileClient.class);
    private final AdCandidateSearchService candidateSearchService = mock(AdCandidateSearchService.class);
    private final AdMatcher adMatcher = new DefaultAdMatcher();
    private final AdRanker adRanker = new MaxBidAdRanker();
    private final AdBudgetService adBudgetService = mock(AdBudgetService.class);

    @Test
    @DisplayName("프로필과 후보가 정상일 때 타겟 광고를 반환한다.")
    void servesTargetedAdWhenProfileIsAvailable() {
        AdServingService service = new AdServingService(userProfileClient, candidateSearchService, adMatcher, adRanker, adBudgetService, 30, 50);
        UserProfile profile = new UserProfile("1", "M", "1:11", 29, List.of("fashion"));
        AdDocument targeted = ad(1L, "M", "1:11", List.of("fashion"), "3000");
        AdDocument broad = ad(2L, "ALL", "0", List.of("finance"), "5000");

        when(userProfileClient.getUserProfile("1")).thenReturn(Optional.of(profile));
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(broad, targeted));
        when(adBudgetService.trySpend(any())).thenReturn(true);

        AdServingResult result = service.serve("1", "home");

        assertThat(result.fallback()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo(ServingFallbackReason.NONE);
        assertThat(result.selectedAd().getId()).isEqualTo(1L);
        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.matchedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("프로필이 없으면 기본 후보로 fallback 한다.")
    void fallsBackWhenProfileIsMissing() {
        AdServingService service = new AdServingService(userProfileClient, candidateSearchService, adMatcher, adRanker, adBudgetService, 30, 50);
        AdDocument broad = ad(2L, "ALL", "0", List.of("finance"), "5000");

        when(userProfileClient.getUserProfile("missing")).thenReturn(Optional.empty());
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(broad));
        when(adBudgetService.trySpend(any())).thenReturn(true);

        AdServingResult result = service.serve("missing", "home");

        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(ServingFallbackReason.PROFILE_NOT_FOUND);
        assertThat(result.selectedAd().getId()).isEqualTo(2L);
        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.matchedCount()).isZero();
    }

    @Test
    @DisplayName("프로필은 있지만 타겟에 맞는 후보가 없으면 기본 후보로 fallback 한다.")
    void fallsBackWhenTargetDoesNotMatch() {
        AdServingService service = new AdServingService(userProfileClient, candidateSearchService, adMatcher, adRanker, adBudgetService, 30, 50);
        UserProfile profile = new UserProfile("1", "M", "1:11", 29, List.of("fashion"));
        AdDocument broad = ad(2L, "F", "2:21", List.of("interior"), "5000");

        when(userProfileClient.getUserProfile("1")).thenReturn(Optional.of(profile));
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(broad));
        when(adBudgetService.trySpend(any())).thenReturn(true);

        AdServingResult result = service.serve("1", "home");

        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(ServingFallbackReason.TARGET_NOT_MATCHED);
        assertThat(result.selectedAd().getId()).isEqualTo(2L);
        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.matchedCount()).isZero();
    }

    @Test
    @DisplayName("DMP 조회가 timeout을 넘으면 기본 후보로 fallback 한다.")
    void fallsBackWhenDmpTimeouts() {
        AdServingService service = new AdServingService(userProfileClient, candidateSearchService, adMatcher, adRanker, adBudgetService, 10, 50);
        AdDocument broad = ad(2L, "ALL", "0", List.of("finance"), "5000");

        when(userProfileClient.getUserProfile("slow")).thenAnswer(invocation -> {
            Thread.sleep(100);
            return Optional.of(new UserProfile("slow", "M", "1:11", 29, List.of("fashion")));
        });
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(broad));
        when(adBudgetService.trySpend(any())).thenReturn(true);

        AdServingResult result = service.serve("slow", "home");

        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(ServingFallbackReason.DMP_TIMEOUT);
        assertThat(result.selectedAd().getId()).isEqualTo(2L);
        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.matchedCount()).isZero();
    }

    @Test
    @DisplayName("후보 조회가 timeout을 넘으면 candidate timeout으로 fallback 한다.")
    void fallsBackWhenCandidateSearchTimeouts() {
        AdServingService service = new AdServingService(userProfileClient, candidateSearchService, adMatcher, adRanker, adBudgetService, 30, 10);

        when(userProfileClient.getUserProfile("1"))
                .thenReturn(Optional.of(new UserProfile("1", "M", "1:11", 29, List.of("fashion"))));
        when(candidateSearchService.searchCandidates("home")).thenAnswer(invocation -> {
            Thread.sleep(100);
            return List.of(ad(1L, "M", "1:11", List.of("fashion"), "3000"));
        });

        AdServingResult result = service.serve("1", "home");

        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(ServingFallbackReason.CANDIDATE_TIMEOUT);
        assertThat(result.selectedAd()).isNull();
        assertThat(result.candidateCount()).isZero();
        assertThat(result.matchedCount()).isZero();
    }

    @Test
    @DisplayName("가장 높은 입찰가 후보의 예산이 없으면 다음 후보를 선택한다.")
    void skipsCandidateWhenBudgetIsExhausted() {
        AdServingService service = new AdServingService(userProfileClient, candidateSearchService, adMatcher, adRanker, adBudgetService, 30, 50);
        UserProfile profile = new UserProfile("1", "M", "1:11", 29, List.of("fashion"));
        AdDocument expensive = ad(1L, "M", "1:11", List.of("fashion"), "5000");
        AdDocument available = ad(2L, "M", "1:11", List.of("fashion"), "3000");

        when(userProfileClient.getUserProfile("1")).thenReturn(Optional.of(profile));
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(expensive, available));
        when(adBudgetService.trySpend(expensive)).thenReturn(false);
        when(adBudgetService.trySpend(available)).thenReturn(true);

        AdServingResult result = service.serve("1", "home");

        assertThat(result.fallback()).isFalse();
        assertThat(result.fallbackReason()).isEqualTo(ServingFallbackReason.NONE);
        assertThat(result.selectedAd().getId()).isEqualTo(2L);
        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.matchedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("모든 후보의 예산이 없으면 BUDGET_EXHAUSTED로 응답한다.")
    void returnsBudgetExhaustedWhenNoCandidateCanSpend() {
        AdServingService service = new AdServingService(userProfileClient, candidateSearchService, adMatcher, adRanker, adBudgetService, 30, 50);
        UserProfile profile = new UserProfile("1", "M", "1:11", 29, List.of("fashion"));
        AdDocument candidate = ad(1L, "M", "1:11", List.of("fashion"), "5000");

        when(userProfileClient.getUserProfile("1")).thenReturn(Optional.of(profile));
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(candidate));
        when(adBudgetService.trySpend(candidate)).thenReturn(false);

        AdServingResult result = service.serve("1", "home");

        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(ServingFallbackReason.BUDGET_EXHAUSTED);
        assertThat(result.selectedAd()).isNull();
        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.matchedCount()).isEqualTo(1);
    }

    private AdDocument ad(Long id, String gender, String locationId, List<String> tags, String bid) {
        return AdDocument.builder()
                .id(id)
                .advertiserId(1L)
                .title("ad-" + id)
                .maxBid(new BigDecimal(bid))
                .totalBudget(new BigDecimal("1000000"))
                .spentAmount(BigDecimal.ZERO)
                .status(AdStatus.ACTIVE)
                .targetGender(gender)
                .targetLocationId(locationId)
                .interestTags(tags)
                .build();
    }
}
