package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAdSlotMatcherTest {

    private final DefaultAdSlotMatcher matcher = new DefaultAdSlotMatcher();

    @Test
    @DisplayName("요청 지면과 일치하거나 전체 지면인 광고만 반환한다")
    void matchesRequestedAndAllSlots() {
        AdDocument home = ad(1L, List.of("home"), 500);
        AdDocument local = ad(2L, List.of("local"), 500);
        AdDocument all = ad(3L, List.of(AdDocument.ALL_SLOTS), 500);

        List<AdDocument> result = matcher.match(List.of(home, local, all), "HOME");

        assertThat(result).extracting(AdDocument::getId).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("지면 최저 입찰가(Floor Price) 미만인 광고는 하드 가드에서 탈락시킨다")
    void filtersOutAdsBelowSlotFloorPrice() {
        AdDocument lowBid = ad(1L, List.of("home"), 300);
        AdDocument exactFloorBid = ad(2L, List.of("home"), 500);
        AdDocument highBid = ad(3L, List.of("home"), 1200);

        List<AdDocument> result = matcher.match(
                List.of(lowBid, exactFloorBid, highBid),
                "home",
                BigDecimal.valueOf(500)
        );

        assertThat(result).extracting(AdDocument::getId).containsExactly(2L, 3L);
        assertThat(matcher.isAboveFloorPrice(lowBid, BigDecimal.valueOf(500))).isFalse();
        assertThat(matcher.isAboveFloorPrice(exactFloorBid, BigDecimal.valueOf(500))).isTrue();
    }

    @Test
    @DisplayName("지면 정보가 없는 광고와 빈 요청 지면은 fail-closed 처리한다")
    void rejectsMissingSlotData() {
        AdDocument missing = ad(1L, List.of(), 500);

        assertThat(matcher.match(List.of(missing), "home")).isEmpty();
        assertThat(matcher.match(List.of(ad(2L, List.of("home"), 500)), " ")).isEmpty();
    }

    private AdDocument ad(Long id, List<String> slotIds, int maxBid) {
        return AdDocument.builder()
                .id(id)
                .slotIds(slotIds)
                .maxBid(BigDecimal.valueOf(maxBid))
                .build();
    }
}
