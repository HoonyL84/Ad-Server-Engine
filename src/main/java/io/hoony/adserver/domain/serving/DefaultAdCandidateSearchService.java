package io.hoony.adserver.domain.serving;

import io.hoony.adserver.config.TracingSupport;
import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.ad.search.AdSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class DefaultAdCandidateSearchService implements AdCandidateSearchService {

    private static final int MAX_CANDIDATES = 200;

    private final AdSearchRepository adSearchRepository;
    private final TracingSupport tracingSupport;
    private final long candidateCacheTtlMs;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<String, CachedCandidates> cachedCandidates = new ConcurrentHashMap<>();

    public DefaultAdCandidateSearchService(
            AdSearchRepository adSearchRepository,
            TracingSupport tracingSupport,
            @Value("${ad-server.serving.candidate-cache-ttl-ms:100}") long candidateCacheTtlMs
    ) {
        this.adSearchRepository = adSearchRepository;
        this.tracingSupport = tracingSupport;
        this.candidateCacheTtlMs = candidateCacheTtlMs;
    }

    @Override
    public List<AdDocument> searchCandidates(String slotId) {
        String normalizedSlotId = normalizeSlotId(slotId);
        if (normalizedSlotId == null) {
            return List.of();
        }

        long now = System.nanoTime();
        CachedCandidates current = cachedCandidates.getOrDefault(normalizedSlotId, CachedCandidates.expired());
        if (current.isValid(now)) {
            return current.candidates();
        }

        ReentrantLock lock = locks.computeIfAbsent(normalizedSlotId, ignored -> new ReentrantLock());
        boolean acquired;
        try {
            acquired = lock.tryLock(10, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Candidate search tryLock interrupted. Returning cached candidates.");
            return current.candidates();
        }

        if (!acquired) {
            log.warn("Candidate search lock acquisition timed out (10ms). Returning cached candidates (size={}).", current.candidates().size());
            return current.candidates();
        }

        try {
            current = cachedCandidates.getOrDefault(normalizedSlotId, CachedCandidates.expired());
            if (current.isValid(System.nanoTime())) {
                return current.candidates();
            }

            List<AdDocument> candidates = tracingSupport.observe(
                    "ad.elasticsearch.candidates",
                    "slot.id",
                    normalizedSlotId,
                    () -> loadCandidates(normalizedSlotId)
            );
            cachedCandidates.put(normalizedSlotId, CachedCandidates.from(candidates, candidateCacheTtlMs));
            return candidates;
        } finally {
            lock.unlock();
        }
    }

    private List<AdDocument> loadCandidates(String slotId) {
        PageRequest pageRequest = PageRequest.of(
                0,
                MAX_CANDIDATES,
                Sort.by(Sort.Direction.DESC, "maxBid")
        );

        long startedAt = System.nanoTime();
        List<AdDocument> candidates = List.copyOf(adSearchRepository.findByStatusAndSlotIdsIn(
                AdStatus.ACTIVE,
                List.of(slotId, AdDocument.ALL_SLOTS),
                pageRequest
        ));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        log.debug(
                "Candidate cache refreshed. slotId={}, size={}, elapsedMs={}, ttlMs={}",
                slotId,
                candidates.size(),
                elapsedMs,
                candidateCacheTtlMs
        );

        return candidates;
    }

    private String normalizeSlotId(String slotId) {
        if (slotId == null || slotId.isBlank()) {
            return null;
        }
        return slotId.trim().toLowerCase(Locale.ROOT);
    }

    private record CachedCandidates(List<AdDocument> candidates, long expiresAtNanos) {

        static CachedCandidates expired() {
            return new CachedCandidates(List.of(), 0L);
        }

        static CachedCandidates from(List<AdDocument> candidates, long ttlMs) {
            long safeTtlMs = Math.max(ttlMs, 0L);
            long expiresAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(safeTtlMs);
            return new CachedCandidates(candidates, expiresAtNanos);
        }

        boolean isValid(long nowNanos) {
            return nowNanos < expiresAtNanos;
        }
    }
}
