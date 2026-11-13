package io.hoony.adserver.domain.openrtb;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hoony.adserver.config.TracingSupport;
import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.openrtb.dto.Bid;
import io.hoony.adserver.domain.openrtb.dto.BidRequest;
import io.hoony.adserver.domain.openrtb.dto.Imp;
import io.hoony.adserver.domain.serving.AdBudgetService;
import io.hoony.adserver.domain.serving.AdCandidateSearchService;
import io.hoony.adserver.domain.serving.AdMatcher;
import io.hoony.adserver.domain.serving.AdSlotMatcher;
import io.hoony.adserver.domain.serving.DefaultAdMatcher;
import io.hoony.adserver.domain.serving.DefaultAdSlotMatcher;
import io.hoony.adserver.domain.serving.MaxBidAdRanker;
import io.hoony.adserver.domain.serving.SimpleCircuitBreaker;
import io.hoony.adserver.domain.user.profile.UserProfile;
import io.hoony.adserver.domain.user.profile.UserProfileClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenRtbBidderServiceTest {

    private final UserProfileClient userProfileClient = mock(UserProfileClient.class);
    private final AdCandidateSearchService candidateSearchService = mock(AdCandidateSearchService.class);
    private final AdSlotMatcher adSlotMatcher = new DefaultAdSlotMatcher();
    private final AdMatcher adMatcher = new DefaultAdMatcher();
    private final MaxBidAdRanker adRanker = new MaxBidAdRanker();
    private final AdBudgetService adBudgetService = mock(AdBudgetService.class);
    private final OpenRtbMetrics openRtbMetrics = new OpenRtbMetrics(new SimpleMeterRegistry());
    private final OpenRtbTrackingUrlBuilder trackingUrlBuilder = new OpenRtbTrackingUrlBuilder(new ObjectMapper());
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private final SimpleCircuitBreaker simpleCircuitBreaker = mock(SimpleCircuitBreaker.class);

    @BeforeEach
    void setUp() {
        when(simpleCircuitBreaker.allowRequest()).thenReturn(true);
        when(adBudgetService.filterExhausted(any())).thenAnswer(invocation -> {
            List<AdDocument> ads = invocation.getArgument(0);
            if (ads == null) {
                return List.of();
            }
            return ads.stream().filter(ad -> !adBudgetService.isExhausted(ad)).toList();
        });
    }

    @AfterEach
    void tearDown() {
        executorService.close();
    }

    private OpenRtbBidderService createService(long slaTimeoutMs, long dmpTimeoutMs) {
        return new OpenRtbBidderService(
                userProfileClient,
                candidateSearchService,
                adSlotMatcher,
                adMatcher,
                Map.of("max-bid", adRanker),
                "max-bid",
                adBudgetService,
                openRtbMetrics,
                trackingUrlBuilder,
                executorService,
                simpleCircuitBreaker,
                new TracingSupport(ObservationRegistry.NOOP),
                slaTimeoutMs,
                dmpTimeoutMs,
                "ad-server-engine",
                "KRW"
        );
    }

    @Test
    @DisplayName("유효한 BidRequest와 매칭 광고가 있으면 최고 입찰가 BidResponse를 반환한다.")
    void returnsHighestMatchingBidResponse() {
        OpenRtbBidderService service = createService(50, 30);
        UserProfile profile = new UserProfile("user-1", "M", "1:11", 29, List.of("fashion"));
        AdDocument lowerBid = ad(1L, List.of("home"), "M", "1:11", List.of("fashion"), "400");
        AdDocument higherBid = ad(2L, List.of("home"), "M", "1:11", List.of("fashion"), "1200");

        when(userProfileClient.getUserProfile("user-1")).thenReturn(Optional.of(profile));
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(lowerBid, higherBid));
        when(adBudgetService.trySpend(any())).thenReturn(true);

        BidRequest request = new BidRequest(
                "req-1",
                List.of(new Imp("imp-1", "home", new BigDecimal("300"))),
                new BidRequest.User("user-1"),
                50
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isTrue();
        assertThat(result.noBidReason()).isEqualTo(NoBidReason.NONE);
        assertThat(result.bidResponse().id()).isEqualTo("req-1");
        assertThat(result.bidResponse().cur()).isEqualTo("KRW");
        assertThat(result.bidResponse().seatbid()).hasSize(1);
        assertThat(result.bidResponse().seatbid().get(0).seat()).isEqualTo("ad-server-engine");

        Bid bid = result.bidResponse().seatbid().get(0).bid().get(0);
        assertThat(bid.impid()).isEqualTo("imp-1");
        assertThat(bid.adid()).isEqualTo("2");
        assertThat(bid.price()).isEqualByComparingTo("1200");
        assertThat(bid.nurl()).contains("/api/v1/openrtb/win")
                .contains("adId=2")
                .contains("price=${AUCTION_PRICE}");
        assertThat(bid.lurl()).contains("/api/v1/openrtb/loss")
                .contains("adId=2")
                .contains("reason=${AUCTION_LOSS}");
        assertThat(bid.adm()).contains("/api/v1/ad-events/impressions")
                .contains("/api/v1/ad-events/clicks");
    }

    @Test
    @DisplayName("user 정보가 없는 요청도 지면과 floor 조건을 만족하면 DMP 호출 없이 입찰한다.")
    void bidsContextuallyWithoutUserProfile() {
        OpenRtbBidderService service = createService(50, 30);
        AdDocument ad = ad(10L, List.of("fashion"), "ALL", "0", List.of(), "800");

        when(candidateSearchService.searchCandidates("fashion")).thenReturn(List.of(ad));
        when(adBudgetService.trySpend(ad)).thenReturn(true);

        BidRequest request = new BidRequest(
                "req-ctx",
                List.of(new Imp("imp-1", "fashion", new BigDecimal("500"))),
                null,
                50
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isTrue();
        assertThat(result.bidResponse().seatbid().get(0).bid().get(0).adid()).isEqualTo("10");
        verify(userProfileClient, never()).getUserProfile(any());
    }

    @Test
    @DisplayName("bidfloor 미만 후보만 있으면 No-Bid(BELOW_FLOOR)를 반환하고 예산을 차감하지 않는다.")
    void returnsNoBidWhenAllCandidatesBelowFloor() {
        OpenRtbBidderService service = createService(50, 30);
        AdDocument lowBid = ad(1L, List.of("home"), "ALL", "0", List.of(), "450");

        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(lowBid));

        BidRequest request = new BidRequest(
                "req-floor",
                List.of(new Imp("imp-1", "home", new BigDecimal("500"))),
                null,
                50
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isFalse();
        assertThat(result.noBidReason()).isEqualTo(NoBidReason.BELOW_FLOOR);
        verify(adBudgetService, never()).trySpend(any());
    }

    @Test
    @DisplayName("슬롯이 일치하지 않으면 No-Bid(SLOT_UNMATCHED)를 반환한다.")
    void returnsNoBidWhenSlotUnmatched() {
        OpenRtbBidderService service = createService(50, 30);
        AdDocument localAd = ad(1L, List.of("local"), "ALL", "0", List.of(), "1000");

        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(localAd));

        BidRequest request = new BidRequest(
                "req-slot",
                List.of(new Imp("imp-1", "home", new BigDecimal("100"))),
                null,
                50
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isFalse();
        assertThat(result.noBidReason()).isEqualTo(NoBidReason.SLOT_UNMATCHED);
        verify(adBudgetService, never()).trySpend(any());
    }

    @Test
    @DisplayName("모든 후보의 예산이 부족하면 No-Bid(BUDGET_EXHAUSTED)를 반환한다.")
    void returnsNoBidWhenBudgetExhausted() {
        OpenRtbBidderService service = createService(50, 30);
        AdDocument candidate = ad(1L, List.of("home"), "ALL", "0", List.of(), "1000");

        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(candidate));
        when(adBudgetService.trySpend(candidate)).thenReturn(false);

        BidRequest request = new BidRequest(
                "req-budget",
                List.of(new Imp("imp-1", "home", new BigDecimal("100"))),
                null,
                50
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isFalse();
        assertThat(result.noBidReason()).isEqualTo(NoBidReason.BUDGET_EXHAUSTED);
    }

    @Test
    @DisplayName("후보가 없으면 No-Bid(NO_CANDIDATE)를 반환한다.")
    void returnsNoBidWhenNoCandidatesFound() {
        OpenRtbBidderService service = createService(50, 30);
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of());

        BidRequest request = new BidRequest(
                "req-empty",
                List.of(new Imp("imp-1", "home", new BigDecimal("100"))),
                null,
                50
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isFalse();
        assertThat(result.noBidReason()).isEqualTo(NoBidReason.NO_CANDIDATE);
    }

    @Test
    @DisplayName("후보 조회가 SLA 타임아웃을 초과하면 No-Bid(CANDIDATE_TIMEOUT)를 반환한다.")
    void returnsNoBidOnCandidateTimeout() {
        OpenRtbBidderService service = createService(15, 8);
        when(candidateSearchService.searchCandidates("home")).thenAnswer(invocation -> {
            Thread.sleep(80);
            return List.of(ad(1L, List.of("home"), "ALL", "0", List.of(), "1000"));
        });

        BidRequest request = new BidRequest(
                "req-cand-timeout",
                List.of(new Imp("imp-1", "home", new BigDecimal("100"))),
                null,
                15
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isFalse();
        assertThat(result.noBidReason()).isEqualTo(NoBidReason.CANDIDATE_TIMEOUT);
    }

    @Test
    @DisplayName("DMP 조회가 SLA 타임아웃을 초과하면 서킷 브레이커를 오염시키지 않고 No-Bid(DMP_TIMEOUT)를 반환한다.")
    void returnsNoBidOnDmpTimeoutWithoutTrippingServingCircuitBreaker() {
        OpenRtbBidderService service = createService(15, 8);
        AdDocument candidate = ad(1L, List.of("home"), "M", "1:11", List.of("fashion"), "1000");

        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(candidate));
        when(userProfileClient.getUserProfile("slow-user")).thenAnswer(invocation -> {
            Thread.sleep(80);
            return Optional.of(new UserProfile("slow-user", "M", "1:11", 29, List.of("fashion")));
        });

        BidRequest request = new BidRequest(
                "req-dmp-timeout",
                List.of(new Imp("imp-1", "home", new BigDecimal("100"))),
                new BidRequest.User("slow-user"),
                15
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isFalse();
        assertThat(result.noBidReason()).isEqualTo(NoBidReason.DMP_TIMEOUT);
        verify(simpleCircuitBreaker, never()).recordFailure();
        verify(adBudgetService, never()).trySpend(any());
    }

    @Test
    @DisplayName("타겟 조건에 맞는 광고가 없으면 No-Bid(TARGET_NOT_MATCHED)를 반환한다.")
    void returnsNoBidWhenTargetDoesNotMatch() {
        OpenRtbBidderService service = createService(50, 30);
        UserProfile profile = new UserProfile("user-1", "M", "1:11", 29, List.of("fashion"));
        AdDocument femaleOnly = ad(1L, List.of("home"), "F", "2:21", List.of("interior"), "1000");

        when(userProfileClient.getUserProfile("user-1")).thenReturn(Optional.of(profile));
        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(femaleOnly));

        BidRequest request = new BidRequest(
                "req-target",
                List.of(new Imp("imp-1", "home", new BigDecimal("100"))),
                new BidRequest.User("user-1"),
                50
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isFalse();
        assertThat(result.noBidReason()).isEqualTo(NoBidReason.TARGET_NOT_MATCHED);
    }

    @Test
    @DisplayName("최고 입찰가 후보의 예산이 소진되었으면 floor 이상인 차순위 가용 후보로 입찰한다.")
    void selectsNextSpendableCandidateAboveFloor() {
        OpenRtbBidderService service = createService(50, 30);
        AdDocument exhaustedTop = ad(1L, List.of("home"), "ALL", "0", List.of(), "1500");
        AdDocument spendableSecond = ad(2L, List.of("home"), "ALL", "0", List.of(), "900");

        when(candidateSearchService.searchCandidates("home")).thenReturn(List.of(exhaustedTop, spendableSecond));
        when(adBudgetService.trySpend(exhaustedTop)).thenReturn(false);
        when(adBudgetService.trySpend(spendableSecond)).thenReturn(true);

        BidRequest request = new BidRequest(
                "req-next-budget",
                List.of(new Imp("imp-1", "home", new BigDecimal("500"))),
                null,
                50
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isTrue();
        assertThat(result.bidResponse().seatbid().get(0).bid().get(0).adid()).isEqualTo("2");
        assertThat(result.bidResponse().seatbid().get(0).bid().get(0).price()).isEqualByComparingTo("900");
    }

    @Test
    @DisplayName("DMP 서킷 브레이커가 열려 있으면 유저 타게팅 요청을 즉시 No-Bid(DMP_CIRCUIT_OPEN)로 차단한다.")
    void returnsNoBidWhenDmpCircuitBreakerIsOpen() {
        OpenRtbBidderService service = createService(50, 30);
        when(simpleCircuitBreaker.allowRequest()).thenReturn(false);

        BidRequest request = new BidRequest(
                "req-cb-open",
                List.of(new Imp("imp-1", "home", new BigDecimal("100"))),
                new BidRequest.User("user-1"),
                50
        );

        OpenRtbBidResult result = service.bid(request);

        assertThat(result.hasBid()).isFalse();
        assertThat(result.noBidReason()).isEqualTo(NoBidReason.DMP_CIRCUIT_OPEN);
        verify(candidateSearchService, never()).searchCandidates(any());
        verify(userProfileClient, never()).getUserProfile(any());
    }

    private AdDocument ad(
            Long id,
            List<String> slotIds,
            String gender,
            String locationId,
            List<String> tags,
            String maxBid
    ) {
        return AdDocument.builder()
                .id(id)
                .advertiserId(100L)
                .title("ad-" + id)
                .imageUrl("https://cdn.ad-server.io/img/" + id + ".jpg")
                .clickUrl("https://ad-server.io/click/" + id)
                .maxBid(new BigDecimal(maxBid))
                .totalBudget(new BigDecimal("10000"))
                .spentAmount(BigDecimal.ZERO)
                .status(AdStatus.ACTIVE)
                .targetGender(gender)
                .targetLocationId(locationId)
                .interestTags(tags)
                .slotIds(slotIds)
                .build();
    }
}
