package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaxBidAdRankerTest {

    private final MaxBidAdRanker ranker = new MaxBidAdRanker();

    @Test
    @DisplayName("후보 중 maxBid가 가장 높은 광고를 선택한다.")
    void selectsHighestBidAd() {
        AdDocument lowBid = ad(1L, "1000");
        AdDocument highBid = ad(2L, "3000");
        AdDocument midBid = ad(3L, "2000");

        assertThat(ranker.select(List.of(lowBid, highBid, midBid)))
                .hasValueSatisfying(selected -> assertThat(selected.getId()).isEqualTo(2L));
    }

    @Test
    @DisplayName("후보가 없으면 선택 결과도 비어 있다.")
    void returnsEmptyWhenCandidatesAreEmpty() {
        assertThat(ranker.select(List.of())).isEmpty();
    }

    private AdDocument ad(Long id, String bid) {
        return AdDocument.builder()
                .id(id)
                .advertiserId(1L)
                .title("ad-" + id)
                .maxBid(new BigDecimal(bid))
                .status(AdStatus.ACTIVE)
                .targetGender("ALL")
                .targetLocationId("0")
                .interestTags(List.of())
                .build();
    }
}
