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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DefaultAdCandidateSearchServiceStaleCacheTest {

    @Test
    @DisplayName("캐시가 만료된 후 Elasticsearch 지연 시, lock을 얻지 못한 다른 스레드들은 기존 Stale 캐시를 즉시 반환한다")
    void testStaleWhileRevalidate() throws Exception {
        AdSearchRepository adSearchRepository = mock(AdSearchRepository.class);
        DefaultAdCandidateSearchService service = new DefaultAdCandidateSearchService(
                adSearchRepository,
                new TracingSupport(ObservationRegistry.NOOP),
                50
        );

        AdDocument ad1 = AdDocument.builder().id(1L).maxBid(BigDecimal.valueOf(100)).status(AdStatus.ACTIVE).build();
        AdDocument ad2 = AdDocument.builder().id(2L).maxBid(BigDecimal.valueOf(200)).status(AdStatus.ACTIVE).build();

        // 1. First invocation (Initial cache load, forces sync lock to populate empty cache)
        when(adSearchRepository.findByStatus(eq(AdStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(List.of(ad1));

        List<AdDocument> firstLoad = service.searchCandidates("home");
        assertThat(firstLoad).hasSize(1);
        assertThat(firstLoad.get(0).getId()).isEqualTo(1L);

        // Sleep to let the TTL (50ms) expire
        Thread.sleep(60);

        // 2. Simulate slow Elasticsearch refresh under expired cache
        CountDownLatch esQueryLatch = new CountDownLatch(1);
        CountDownLatch threadStartLatch = new CountDownLatch(1);

        when(adSearchRepository.findByStatus(eq(AdStatus.ACTIVE), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    threadStartLatch.countDown();
                    esQueryLatch.await(5, TimeUnit.SECONDS); // Block thread holding lock
                    return List.of(ad2); // New refreshed cache candidates
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread A: Detects stale cache, acquires lock, and calls loadCandidates() (blocks on slow ES query)
        Future<List<AdDocument>> threadA = executor.submit(() -> service.searchCandidates("home"));

        // Wait until Thread A actually starts loadCandidates() and holds the lock
        threadStartLatch.await(1, TimeUnit.SECONDS);

        // Thread B: Invokes searchCandidates concurrently. Since A holds the lock, tryLock(10ms) fails and B returns Stale cache instantly
        Future<List<AdDocument>> threadB = executor.submit(() -> service.searchCandidates("home"));

        List<AdDocument> resultB = threadB.get(500, TimeUnit.MILLISECONDS);
        // tryLock failed, so return stale cache (1L) instead of blocking or throwing exception
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).getId()).isEqualTo(1L);

        // Release ES query latch to let Thread A complete
        esQueryLatch.countDown();
        List<AdDocument> resultA = threadA.get(500, TimeUnit.MILLISECONDS);

        // Thread A successfully completes and updates cache to the new data (2L)
        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).getId()).isEqualTo(2L);

        executor.shutdown();
    }
}
