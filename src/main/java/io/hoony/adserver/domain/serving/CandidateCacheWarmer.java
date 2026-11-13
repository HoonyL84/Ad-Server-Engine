package io.hoony.adserver.domain.serving;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes candidate caches before TTL expiry when enabled.
 */
@Slf4j
@Component
public class CandidateCacheWarmer {

    private final DefaultAdCandidateSearchService candidateSearchService;
    private final boolean enabled;

    public CandidateCacheWarmer(
            DefaultAdCandidateSearchService candidateSearchService,
            @Value("${ad-server.serving.candidate-cache-warmer.enabled:false}") boolean enabled
    ) {
        this.candidateSearchService = candidateSearchService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${ad-server.serving.candidate-cache-warmer.interval-ms:50}")
    public void warmUp() {
        if (!enabled) {
            return;
        }

        int refreshedSlots = candidateSearchService.warmUpExpiringSlots();
        if (refreshedSlots > 0) {
            log.debug("Candidate cache warmer refreshed {} slot(s).", refreshedSlots);
        }
    }
}
