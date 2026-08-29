package io.hoony.adserver.domain.serving;

import io.hoony.adserver.config.TracingSupport;
import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.ad.search.AdSearchRepository;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAdCandidateSearchServiceTest {

    private final AdSearchRepository adSearchRepository = mock(AdSearchRepository.class);
    private final DefaultAdCandidateSearchService service = new DefaultAdCandidateSearchService(
            adSearchRepository,
            new TracingSupport(ObservationRegistry.NOOP),
            100
    );

    @Test
    @DisplayName("ACTIVE 후보를 maxBid 내림차순으로 정렬한다.")
    void requestsActiveCandidatesByBidDesc() {
        AdDocument low = ad(1L, "1000");
        AdDocument high = ad(2L, "3000");
        AdDocument mid = ad(3L, "2000");

        when(adSearchRepository.findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("home", AdDocument.ALL_SLOTS)),
                any(Pageable.class)
        ))
                .thenReturn(List.of(high, mid, low));

        List<AdDocument> result = service.searchCandidates("home");

        assertThat(result).extracting(AdDocument::getId).containsExactly(2L, 3L, 1L);
        verify(adSearchRepository).findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("home", AdDocument.ALL_SLOTS)),
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getPageNumber() == 0
                                && pageable.getPageSize() == 200
                                && pageable.getSort().getOrderFor("maxBid") != null
                                && pageable.getSort().getOrderFor("maxBid").isDescending()
                )
        );
    }

    @Test
    @DisplayName("짧은 TTL 안에서는 같은 지면의 ES 후보만 재사용한다.")
    void reusesCandidatesWithinShortTtlPerSlot() {
        AdDocument high = ad(2L, "3000");
        AdDocument mid = ad(3L, "2000");

        when(adSearchRepository.findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("fashion", AdDocument.ALL_SLOTS)),
                any(Pageable.class)
        ))
                .thenReturn(List.of(high, mid));

        List<AdDocument> first = service.searchCandidates("fashion");
        List<AdDocument> second = service.searchCandidates("fashion");

        assertThat(first).extracting(AdDocument::getId).containsExactly(2L, 3L);
        assertThat(second).extracting(AdDocument::getId).containsExactly(2L, 3L);
        verify(adSearchRepository, times(1)).findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("fashion", AdDocument.ALL_SLOTS)),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("서로 다른 지면은 후보 캐시와 ES 조회를 공유하지 않는다.")
    void separatesCandidateCacheBySlot() {
        when(adSearchRepository.findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("fashion", AdDocument.ALL_SLOTS)),
                any(Pageable.class)
        )).thenReturn(List.of(ad(1L, "3000")));
        when(adSearchRepository.findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("home", AdDocument.ALL_SLOTS)),
                any(Pageable.class)
        )).thenReturn(List.of(ad(2L, "2000")));

        assertThat(service.searchCandidates("fashion")).extracting(AdDocument::getId).containsExactly(1L);
        assertThat(service.searchCandidates("home")).extracting(AdDocument::getId).containsExactly(2L);
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
                .slotIds(List.of(AdDocument.ALL_SLOTS))
                .build();
    }
}
