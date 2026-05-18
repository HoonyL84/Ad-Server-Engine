package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.adstatistic.AdStatisticDto;
import io.hoony.adserver.domain.adstatistic.AdStatisticService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CtrWeightAdRankerTest {

    private AdStatisticService adStatisticService;
    private CtrWeightAdRanker ctrWeightAdRanker;

    @BeforeEach
    void setUp() {
        adStatisticService = mock(AdStatisticService.class);
        ctrWeightAdRanker = new CtrWeightAdRanker(adStatisticService, 10.0, 1000.0);
    }

    @Test
    @DisplayName("eCPM 점수(입찰가 * 스무딩 CTR)가 높은 순서대로 광고 후보를 정렬한다")
    void shouldRankBySmoothedCtrAndBid() {
        AdDocument ad1 = createAdDocument(1L, 1000);
        AdDocument ad2 = createAdDocument(2L, 2000);
        AdDocument ad3 = createAdDocument(3L, 3000);

        when(adStatisticService.getStatistic(1L)).thenReturn(new AdStatisticDto(10000, 200));
        when(adStatisticService.getStatistic(2L)).thenReturn(new AdStatisticDto(1000, 5));
        when(adStatisticService.getStatistic(3L)).thenReturn(new AdStatisticDto(50000, 100));

        List<AdDocument> candidates = List.of(ad1, ad2, ad3);

        List<AdDocument> ranked = ctrWeightAdRanker.rank(candidates);

        assertThat(ranked).hasSize(3);
        assertThat(ranked.get(0).getId()).isEqualTo(1L);
        assertThat(ranked.get(1).getId()).isEqualTo(2L);
        assertThat(ranked.get(2).getId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("가장 점수가 높은 광고 하나를 선택한다")
    void shouldSelectHighestScoreAd() {
        AdDocument ad1 = createAdDocument(1L, 1000);
        AdDocument ad2 = createAdDocument(2L, 2000);

        when(adStatisticService.getStatistic(1L)).thenReturn(new AdStatisticDto(10000, 200));
        when(adStatisticService.getStatistic(2L)).thenReturn(new AdStatisticDto(1000, 5));

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
                .maxBid(BigDecimal.valueOf(maxBid))
                .build();
    }
}
