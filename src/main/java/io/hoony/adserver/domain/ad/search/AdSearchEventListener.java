package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.config.MdcTraceUtils;
import io.hoony.adserver.domain.ad.event.AdEventPayload;
import io.hoony.adserver.domain.ad.event.AdCreatedEvent;
import io.hoony.adserver.domain.ad.event.AdUpdatedEvent;
import io.hoony.adserver.domain.serving.AdBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdSearchEventListener {

    private static final int MAX_SYNC_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 100L;

    private final AdSearchRepository adSearchRepository;
    private final AdDocumentMapper adDocumentMapper;
    private final AdBudgetService adBudgetService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAdCreatedEvent(AdCreatedEvent event) {
        MdcTraceUtils.withTraceContext(event.getPayload().traceContext(), () -> sync(event.getPayload())).run();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAdUpdatedEvent(AdUpdatedEvent event) {
        MdcTraceUtils.withTraceContext(event.getPayload().traceContext(), () -> {
            if (sync(event.getPayload())) {
                adBudgetService.evictCache(event.getPayload().id());
            }
        }).run();
    }

    private boolean sync(AdEventPayload payload) {
        log.info("Starting ES sync for ad: {}", payload.id());

        for (int attempt = 1; attempt <= MAX_SYNC_ATTEMPTS; attempt++) {
            try {
                adSearchRepository.save(adDocumentMapper.toDocument(payload));
                log.info("Successfully synced ad: {} to Elasticsearch", payload.id());
                return true;
            } catch (Exception e) {
                if (attempt == MAX_SYNC_ATTEMPTS) {
                    log.error("Failed to sync ad: {} to Elasticsearch after {} attempts", payload.id(), attempt, e);
                    return false;
                }

                log.warn("Failed to sync ad: {} to Elasticsearch. attempt={}/{}", payload.id(), attempt, MAX_SYNC_ATTEMPTS, e);
                if (!sleepBeforeRetry(attempt)) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF_MS * attempt);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for Elasticsearch sync retry");
            return false;
        }
    }
}
