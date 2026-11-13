package io.hoony.adserver.domain.serving;

import io.hoony.adserver.config.TracingSupport;
import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.ad.search.AdSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class DefaultAdCandidateSearchService implements AdCandidateSearchService {

    private static final int MAX_CANDIDATES = 200;
    private static final long DEFAULT_REFRESH_AHEAD_MS = 30L;

    private final AdSearchRepository adSearchRepository;
    private final TracingSupport tracingSupport;
    private final long candidateCacheTtlMs;
    private final long refreshAheadNanos;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<String, CachedCandidates> cachedCandidates = new ConcurrentHashMap<>();
    private final Set<String> registeredSlots = ConcurrentHashMap.newKeySet();

    public DefaultAdCandidateSearchService(
            AdSearchRepository adSearchRepository,
            TracingSupport tracingSupport,
            long candidateCacheTtlMs
    ) {
        this(adSearchRepository, tracingSupport, candidateCacheTtlMs, DEFAULT_REFRESH_AHEAD_MS);
    }

    @Autowired
    public DefaultAdCandidateSearchService(
            AdSearchRepository adSearchRepository,
            TracingSupport tracingSupport,
            @Value("${ad-server.serving.candidate-cache-ttl-ms:100}") long candidateCacheTtlMs,
            @Value("${ad-server.serving.candidate-cache-warmer.refresh-ahead-ms:30}") long refreshAheadMs
    ) {
        this.adSearchRepository = adSearchRepository;
        this.tracingSupport = tracingSupport;
        this.candidateCacheTtlMs = candidateCacheTtlMs;
        this.refreshAheadNanos = TimeUnit.MILLISECONDS.toNanos(
                sanitizeRefreshAheadMs(refreshAheadMs, candidateCacheTtlMs)
        );
    }

    @Override
    public List<AdDocument> searchCandidates(String slotId) {
        String normalizedSlotId = normalizeSlotId(slotId);
        if (normalizedSlotId == null) {
            return List.of();
        }

        registeredSlots.add(normalizedSlotId);

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

            return refreshSlotCandidates(normalizedSlotId);
        } finally {
            lock.unlock();
        }
    }

    /** Registers an active slot so background warming keeps its candidate cache fresh. */
    public void registerSlot(String slotId) {
        String normalizedSlotId = normalizeSlotId(slotId);
        if (normalizedSlotId != null) {
            registeredSlots.add(normalizedSlotId);
        }
    }

    /** Proactively refreshes registered slots approaching TTL expiry without blocking request threads. */
    public int warmUpExpiringSlots() {
        int refreshedCount = 0;
        long now = System.nanoTime();
        for (String slotId : registeredSlots) {
            if (warmSlotIfDue(slotId, now)) {
                refreshedCount++;
            }
        }
        return refreshedCount;
    }

    private boolean warmSlotIfDue(String slotId, long now) {
        CachedCandidates current = cachedCandidates.getOrDefault(slotId, CachedCandidates.expired());
        if (!current.needsWarmUp(now, refreshAheadNanos)) {
            return false;
        }

        ReentrantLock lock = locks.computeIfAbsent(slotId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            return false;
        }

        try {
            current = cachedCandidates.getOrDefault(slotId, CachedCandidates.expired());
            if (!current.needsWarmUp(System.nanoTime(), refreshAheadNanos)) {
                return false;
            }
            refreshSlotCandidates(slotId);
            return true;
        } catch (RuntimeException e) {
            log.warn("Candidate cache warm-up failed. slotId={}, preserving cached size={}.",
                    slotId, current.candidates().size(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    private List<AdDocument> refreshSlotCandidates(String normalizedSlotId) {
        List<AdDocument> candidates = tracingSupport.observe(
                "ad.elasticsearch.candidates",
                "slot.id",
                normalizedSlotId,
                () -> loadCandidates(normalizedSlotId)
        );
        cachedCandidates.put(normalizedSlotId, CachedCandidates.from(candidates, candidateCacheTtlMs));
        return candidates;
    }

    private long sanitizeRefreshAheadMs(long refreshAheadMs, long ttlMs) {
        if (ttlMs <= 0L || refreshAheadMs <= 0L) {
            return 0L;
        }
        // Cap refresh-ahead below TTL so newly warmed entries are not immediately due again.
        return refreshAheadMs >= ttlMs ? ttlMs / 2 : refreshAheadMs;
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

    private record CachedCandidates(List<AdDocument> candidates, long expiresAtNanos, boolean populated) {

        static CachedCandidates expired() {
            return new CachedCandidates(List.of(), 0L, false);
        }

        static CachedCandidates from(List<AdDocument> candidates, long ttlMs) {
            long safeTtlMs = Math.max(ttlMs, 0L);
            long expiresAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(safeTtlMs);
            return new CachedCandidates(candidates, expiresAtNanos, true);
        }

        boolean isValid(long nowNanos) {
            return populated && (expiresAtNanos - nowNanos) > 0L;
        }

        boolean needsWarmUp(long nowNanos, long refreshAheadNanos) {
            if (!populated) {
                return true;
            }
            return (expiresAtNanos - refreshAheadNanos - nowNanos) <= 0L;
        }
    }
}
