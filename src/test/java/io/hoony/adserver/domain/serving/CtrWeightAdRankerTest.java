package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.adstatistic.AdStatisticDto;
import io.hoony.adserver.domain.adstatistic.AdStatisticService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CtrWeightAdRankerTest {

    private AdStatisticService adStatisticService;
    private CtrWeightAdRanker ctrWeightAdRanker;

    @BeforeEach
    void setUp() {
        adStatisticService = mock(AdStatisticService.class);
        ctrWeightAdRanker = new CtrWeightAdRanker(adStatisticService, 10.0, 1000.0);
    }

    @Test
    @DisplayName("eCPM 점수(pCTR * Bid * 1000)가 높은 순서대로 광고 후보를 정렬한다")
    void shouldRankBySmoothedCtrAndBid() {
        AdDocument ad1 = createAdDocument(1L, 1000);
        AdDocument ad2 = createAdDocument(2L, 2000);
        AdDocument ad3 = createAdDocument(3L, 3000);

        when(adStatisticService.getStatistics(anyList())).thenReturn(Map.of(
                1L, new AdStatisticDto(10000, 200),
                2L, new AdStatisticDto(1000, 5),
                3L, new AdStatisticDto(50000, 100)
        ));

        List<AdDocument> candidates = List.of(ad1, ad2, ad3);

        List<AdDocument> ranked = ctrWeightAdRanker.rank(candidates);

        assertThat(ranked).hasSize(3);
        assertThat(ranked.get(0).getId()).isEqualTo(1L);
        assertThat(ranked.get(1).getId()).isEqualTo(2L);
        assertThat(ranked.get(2).getId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("[0.1초 순수 POJO] calculateEcpm 공식(pCTR * Bid * 1000)과 베이지안 평활화 수식을 검증한다")
    void shouldCalculateExactEcpmWithoutSpringOrDb() {
        CtrWeightAdRanker pureRanker = new CtrWeightAdRanker(null, 10.0, 1000.0);
        AdStatisticDto stat = new AdStatisticDto(9000L, 190L); // (190 + 10) / (9000 + 1000) = 200 / 10000 = 0.02

        double smoothedCtr = stat.getSmoothedCtr(10.0, 1000.0);
        double ecpm = pureRanker.calculateEcpm(500.0, smoothedCtr); // 0.02 * 500 * 1000 = 10,000

        assertThat(smoothedCtr).isCloseTo(0.02, within(1e-6));
        assertThat(ecpm).isCloseTo(10000.0, within(1e-4));
    }

    @Test
    @DisplayName("[ML pCTR & Fallback] MlPredictor 정상 응답 시 ML pCTR을 쓰고, 10ms 타임아웃 시 베이지안 CTR로 Fallback한다")
    void shouldUseMlPredictorAndFallbackToBayesianOnTimeout() {
        AdDocument fastAd = createAdDocument(1L, 1000);
        AdDocument slowTimeoutAd = createAdDocument(2L, 1000);
        AdStatisticDto stat = new AdStatisticDto(9000L, 90L); // Bayesian CTR = (90+10)/(9000+1000) = 0.01

        MlPredictor predictor = (adId, slotId) -> {
            if (adId.equals(1L)) {
                return OptionalDouble.of(0.055); // ML 추론 성공 (5.5%)
            }
            try {
                Thread.sleep(50L); // 10ms 타임아웃 초과 유도
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return OptionalDouble.of(0.099);
        };

        CtrWeightAdRanker mlRanker = new CtrWeightAdRanker(adStatisticService, 10.0, 1000.0, predictor, 10L);

        assertThat(mlRanker.resolvePredictedCtr(fastAd, stat)).isCloseTo(0.055, within(1e-6));
        assertThat(mlRanker.resolvePredictedCtr(slowTimeoutAd, stat)).isCloseTo(0.01, within(1e-6));
    }

    @Test
    @DisplayName("가장 점수가 높은 광고 하나를 선택한다")
    void shouldSelectHighestScoreAd() {
        AdDocument ad1 = createAdDocument(1L, 1000);
        AdDocument ad2 = createAdDocument(2L, 2000);

        when(adStatisticService.getStatistics(anyList())).thenReturn(Map.of(
                1L, new AdStatisticDto(10000, 200),
                2L, new AdStatisticDto(1000, 5)
        ));

        List<AdDocument> candidates = List.of(ad1, ad2);

        Optional<AdDocument> selected = ctrWeightAdRanker.select(candidates);

        assertThat(selected).isPresent();
        assertThat(selected.get().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("후보군이 비어있으면 빈 결과를 반환한다")
    void shouldReturnEmptyWhenCandidatesAreEmpty() {
        assertThat(ctrWeightAdRanker.rank(List.of())).isEmpty();
        assertThat(ctrWeightAdRanker.select(List.of())).isEmpty();
    }

    private AdDocument createAdDocument(Long id, int maxBid) {
        return AdDocument.builder()
                .id(id)
                .slotIds(List.of("home"))
                .maxBid(BigDecimal.valueOf(maxBid))
                .build();
    }
}
