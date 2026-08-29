package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAdSlotMatcherTest {

    private final DefaultAdSlotMatcher matcher = new DefaultAdSlotMatcher();

    @Test
    @DisplayName("요청 지면과 일치하거나 전체 지면인 광고만 반환한다")
    void matchesRequestedAndAllSlots() {
        AdDocument home = ad(1L, List.of("home"));
        AdDocument local = ad(2L, List.of("local"));
        AdDocument all = ad(3L, List.of(AdDocument.ALL_SLOTS));

        List<AdDocument> result = matcher.match(List.of(home, local, all), "HOME");

        assertThat(result).extracting(AdDocument::getId).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("지면 정보가 없는 광고와 빈 요청 지면은 fail-closed 처리한다")
    void rejectsMissingSlotData() {
        AdDocument missing = ad(1L, List.of());

        assertThat(matcher.match(List.of(missing), "home")).isEmpty();
        assertThat(matcher.match(List.of(ad(2L, List.of("home"))), " ")).isEmpty();
    }

    private AdDocument ad(Long id, List<String> slotIds) {
        return AdDocument.builder()
                .id(id)
                .slotIds(slotIds)
                .build();
    }
}
