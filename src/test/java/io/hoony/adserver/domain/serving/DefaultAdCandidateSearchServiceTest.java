package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.ad.search.AdSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAdCandidateSearchServiceTest {

    private final AdSearchRepository adSearchRepository = mock(AdSearchRepository.class);
    private final DefaultAdCandidateSearchService service = new DefaultAdCandidateSearchService(adSearchRepository);

    @Test
    @DisplayName("ACTIVE 후보를 maxBid 내림차순으로 정렬한다.")
    void requestsActiveCandidatesByBidDesc() {
        AdDocument low = ad(1L, "1000");
        AdDocument high = ad(2L, "3000");
        AdDocument mid = ad(3L, "2000");

        when(adSearchRepository.findByStatus(eq(AdStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(high, mid, low));

        List<AdDocument> result = service.searchCandidates("home");

        assertThat(result).extracting(AdDocument::getId).containsExactly(2L, 3L, 1L);
        verify(adSearchRepository).findByStatus(
                eq(AdStatus.ACTIVE),
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getPageNumber() == 0
                                && pageable.getPageSize() == 200
                                && pageable.getSort().getOrderFor("maxBid") != null
                                && pageable.getSort().getOrderFor("maxBid").isDescending()
                )
        );
    }

    private AdDocument ad(Long id, String bid) {
        return AdDocument.builder()
                .id(id)
                .advertiserId(1L)
                .title("ad-" + id)
                .maxBid(bid == null ? null : new BigDecimal(bid))
                .status(AdStatus.ACTIVE)
                .targetGender("ALL")
                .targetLocationId("0")
                .interestTags(List.of())
                .build();
    }
}
