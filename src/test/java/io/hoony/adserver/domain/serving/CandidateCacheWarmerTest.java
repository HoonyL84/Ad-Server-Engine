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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateCacheWarmerTest {

    @Test
    @DisplayName("warmer 활성화 시 TTL 임박 지면을 선제 갱신해 요청 스레드가 ES 추가 조회 없이 최신 후보를 반환한다")
    void warmsExpiringSlotBeforeTtlSoRequestHitsCacheWithoutEsCall() throws Exception {
        AdSearchRepository adSearchRepository = mock(AdSearchRepository.class);
        DefaultAdCandidateSearchService service = new DefaultAdCandidateSearchService(
                adSearchRepository,
                new TracingSupport(ObservationRegistry.NOOP),
                300,
                220
        );
        CandidateCacheWarmer warmer = new CandidateCacheWarmer(service, true);

        AdDocument initial = ad(1L, "1000");
        AdDocument updated = ad(2L, "2000");
        when(adSearchRepository.findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("home", AdDocument.ALL_SLOTS)),
                any(Pageable.class)
        )).thenReturn(List.of(initial)).thenReturn(List.of(updated));

        assertThat(service.searchCandidates("home")).extracting(AdDocument::getId).containsExactly(1L);

        // Fresh entry should not trigger redundant warm-up queries.
        warmer.warmUp();
        verify(adSearchRepository, times(1)).findByStatusAndSlotIdsIn(any(), any(), any());

        // Cross refresh-ahead threshold (300 - 220 = 80ms) while still before TTL expiry.
        Thread.sleep(100);
        warmer.warmUp();
        verify(adSearchRepository, times(2)).findByStatusAndSlotIdsIn(any(), any(), any());

        List<AdDocument> served = service.searchCandidates("home");
        assertThat(served).extracting(AdDocument::getId).containsExactly(2L);
        verify(adSearchRepository, times(2)).findByStatusAndSlotIdsIn(any(), any(), any());
    }

    @Test
    @DisplayName("warm-up 중 ES 조회 예외가 발생해도 기존 stale 캐시를 덮어쓰지 않고 예외를 전파하지 않는다")
    void preservesStaleCacheAndDoesNotThrowWhenWarmUpFails() throws Exception {
        AdSearchRepository adSearchRepository = mock(AdSearchRepository.class);
        DefaultAdCandidateSearchService service = new DefaultAdCandidateSearchService(
                adSearchRepository,
                new TracingSupport(ObservationRegistry.NOOP),
                60,
                30
        );
        CandidateCacheWarmer warmer = new CandidateCacheWarmer(service, true);

        AdDocument staleAd = ad(1L, "1000");
        AdDocument freshAd = ad(2L, "3000");
        CountDownLatch esQueryStarted = new CountDownLatch(1);
        CountDownLatch releaseEsQuery = new CountDownLatch(1);

        when(adSearchRepository.findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("fashion", AdDocument.ALL_SLOTS)),
                any(Pageable.class)
        ))
                .thenReturn(List.of(staleAd))
                .thenThrow(new RuntimeException("ES unavailable"))
                .thenAnswer(invocation -> {
                    esQueryStarted.countDown();
                    releaseEsQuery.await(5, TimeUnit.SECONDS);
                    return List.of(freshAd);
                });

        assertThat(service.searchCandidates("fashion")).extracting(AdDocument::getId).containsExactly(1L);

        Thread.sleep(75);
        assertThatCode(warmer::warmUp).doesNotThrowAnyException();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<AdDocument>> refreshingThread = executor.submit(() -> service.searchCandidates("fashion"));
            assertThat(esQueryStarted.await(1, TimeUnit.SECONDS)).isTrue();

            Future<List<AdDocument>> concurrentThread = executor.submit(() -> service.searchCandidates("fashion"));
            List<AdDocument> staleFallback = concurrentThread.get(500, TimeUnit.MILLISECONDS);
            assertThat(staleFallback).extracting(AdDocument::getId).containsExactly(1L);

            releaseEsQuery.countDown();
            assertThat(refreshingThread.get(500, TimeUnit.MILLISECONDS))
                    .extracting(AdDocument::getId)
                    .containsExactly(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("warmer 비활성화 상태에서는 백그라운드 ES 선제 조회를 수행하지 않고 기존 경로를 유지한다")
    void doesNothingWhenWarmerDisabled() throws Exception {
        AdSearchRepository adSearchRepository = mock(AdSearchRepository.class);
        DefaultAdCandidateSearchService service = new DefaultAdCandidateSearchService(
                adSearchRepository,
                new TracingSupport(ObservationRegistry.NOOP),
                60,
                30
        );
        CandidateCacheWarmer disabledWarmer = new CandidateCacheWarmer(service, false);

        when(adSearchRepository.findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("home", AdDocument.ALL_SLOTS)),
                any(Pageable.class)
        )).thenReturn(List.of(ad(1L, "1000"))).thenReturn(List.of(ad(2L, "2000")));

        assertThat(service.searchCandidates("home")).extracting(AdDocument::getId).containsExactly(1L);
        Thread.sleep(45);

        disabledWarmer.warmUp();
        verify(adSearchRepository, times(1)).findByStatusAndSlotIdsIn(any(), any(), any());
    }

    @Test
    @DisplayName("등록된 지면만 선제 워밍하고 미등록 지면은 조회하지 않는다")
    void warmsOnlyRegisteredSlots() {
        AdSearchRepository adSearchRepository = mock(AdSearchRepository.class);
        DefaultAdCandidateSearchService service = new DefaultAdCandidateSearchService(
                adSearchRepository,
                new TracingSupport(ObservationRegistry.NOOP),
                300,
                100
        );
        CandidateCacheWarmer warmer = new CandidateCacheWarmer(service, true);

        when(adSearchRepository.findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("local", AdDocument.ALL_SLOTS)),
                any(Pageable.class)
        )).thenReturn(List.of(ad(10L, "1500")));

        service.registerSlot(" LOCAL ");
        warmer.warmUp();

        verify(adSearchRepository, times(1)).findByStatusAndSlotIdsIn(
                eq(AdStatus.ACTIVE),
                eq(List.of("local", AdDocument.ALL_SLOTS)),
                any(Pageable.class)
        );
        assertThat(service.searchCandidates("local")).extracting(AdDocument::getId).containsExactly(10L);
        verify(adSearchRepository, times(1)).findByStatusAndSlotIdsIn(any(), any(), any());
    }

    @Test
    @DisplayName("warmer ON/OFF 비교 시 warmer ON 경로에서는 TTL 경과 후에도 요청 스레드 ES 조회가 0회다")
    void comparesRequestThreadEsLookupsWithWarmerOnVsOff() throws Exception {
        java.util.concurrent.atomic.AtomicInteger onRequestEsCalls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger offRequestEsCalls = new java.util.concurrent.atomic.AtomicInteger();

        AdSearchRepository repoOn = mock(AdSearchRepository.class);
        when(repoOn.findByStatusAndSlotIdsIn(any(), any(), any())).thenAnswer(invocation -> {
            if ("request-thread".equals(Thread.currentThread().getName())) {
                onRequestEsCalls.incrementAndGet();
            }
            return List.of(ad(1L, "1000"));
        });

        AdSearchRepository repoOff = mock(AdSearchRepository.class);
        when(repoOff.findByStatusAndSlotIdsIn(any(), any(), any())).thenAnswer(invocation -> {
            if ("request-thread".equals(Thread.currentThread().getName())) {
                offRequestEsCalls.incrementAndGet();
            }
            return List.of(ad(1L, "1000"));
        });

        DefaultAdCandidateSearchService serviceOn = new DefaultAdCandidateSearchService(
                repoOn, new TracingSupport(ObservationRegistry.NOOP), 80, 50
        );
        DefaultAdCandidateSearchService serviceOff = new DefaultAdCandidateSearchService(
                repoOff, new TracingSupport(ObservationRegistry.NOOP), 80, 50
        );
        CandidateCacheWarmer warmerOn = new CandidateCacheWarmer(serviceOn, true);
        CandidateCacheWarmer warmerOff = new CandidateCacheWarmer(serviceOff, false);

        ExecutorService requestExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "request-thread"));
        try {
            requestExecutor.submit(() -> serviceOn.searchCandidates("home")).get(1, TimeUnit.SECONDS);
            requestExecutor.submit(() -> serviceOff.searchCandidates("home")).get(1, TimeUnit.SECONDS);

            // Reset counters after cold start; measure steady-state request-thread ES hits.
            onRequestEsCalls.set(0);
            offRequestEsCalls.set(0);

            Thread.sleep(45);
            warmerOn.warmUp();
            warmerOff.warmUp();

            Thread.sleep(45);
            requestExecutor.submit(() -> serviceOn.searchCandidates("home")).get(1, TimeUnit.SECONDS);
            requestExecutor.submit(() -> serviceOff.searchCandidates("home")).get(1, TimeUnit.SECONDS);

            assertThat(onRequestEsCalls.get()).isZero();
            assertThat(offRequestEsCalls.get()).isEqualTo(1);
        } finally {
            requestExecutor.shutdownNow();
        }
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
                .slotIds(List.of(AdDocument.ALL_SLOTS))
                .build();
    }
}
