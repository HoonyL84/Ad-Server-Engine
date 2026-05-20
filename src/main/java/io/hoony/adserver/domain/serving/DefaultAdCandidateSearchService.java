package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.ad.search.AdSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DefaultAdCandidateSearchService implements AdCandidateSearchService {

    private static final int MAX_CANDIDATES = 200;

    private final AdSearchRepository adSearchRepository;
    private final long candidateCacheTtlMs;
    private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
    private volatile CachedCandidates cachedCandidates = CachedCandidates.expired();

    public DefaultAdCandidateSearchService(
            AdSearchRepository adSearchRepository,
            @Value("${ad-server.serving.candidate-cache-ttl-ms:100}") long candidateCacheTtlMs
    ) {
        this.adSearchRepository = adSearchRepository;
        this.candidateCacheTtlMs = candidateCacheTtlMs;
    }

    @Override
    public List<AdDocument> searchCandidates(String slotId) {
        long now = System.nanoTime();
        CachedCandidates current = cachedCandidates;
        if (current.isValid(now)) {
            return current.candidates();
        }

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
            current = cachedCandidates;
            if (current.isValid(now)) {
                return current.candidates();
            }

            List<AdDocument> candidates = loadCandidates();
            cachedCandidates = CachedCandidates.from(candidates, candidateCacheTtlMs);
            return candidates;
        } finally {
            lock.unlock();
        }
    }

    private List<AdDocument> loadCandidates() {
        PageRequest pageRequest = PageRequest.of(
                0,
                MAX_CANDIDATES,
                Sort.by(Sort.Direction.DESC, "maxBid")
        );

        long startedAt = System.nanoTime();
        List<AdDocument> candidates = List.copyOf(adSearchRepository.findByStatus(AdStatus.ACTIVE, pageRequest));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        log.debug(
                "Candidate cache refreshed. size={}, elapsedMs={}, ttlMs={}",
                candidates.size(),
                elapsedMs,
                candidateCacheTtlMs
        );

        return candidates;
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
