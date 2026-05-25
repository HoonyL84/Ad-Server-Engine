package io.hoony.adserver.domain.ad.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hoony.adserver.config.MdcTraceUtils;
import io.hoony.adserver.domain.ad.event.AdEventPayload;
import io.hoony.adserver.domain.serving.AdBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdSearchOutboxService {

    private final AdSearchOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final AdSearchRepository adSearchRepository;
    private final AdDocumentMapper adDocumentMapper;
    private final AdBudgetService adBudgetService;

    @Value("${ad-server.search-outbox.max-attempts:5}")
    private int maxAttempts;

    public void enqueue(AdSearchOutboxEventType eventType, AdEventPayload payload, String error) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            outboxRepository.save(AdSearchOutbox.pending(payload.id(), eventType, payloadJson, error));
            log.warn("Queued ES sync outbox. adId={}, eventType={}, reason={}", payload.id(), eventType, error);
        } catch (Exception e) {
            log.error("Failed to queue ES sync outbox. adId={}, eventType={}", payload.id(), eventType, e);
        }
    }

    @Scheduled(fixedDelayString = "${ad-server.search-outbox.retry-delay-ms:30000}")
    @Transactional
    public void retryPending() {
        LocalDateTime now = LocalDateTime.now();
        var items = outboxRepository.findTop50ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(
                AdSearchOutboxStatus.PENDING,
                now
        );

        for (AdSearchOutbox item : items) {
            retry(item);
        }
    }

    private void retry(AdSearchOutbox item) {
        try {
            AdEventPayload payload = objectMapper.readValue(item.getPayloadJson(), AdEventPayload.class);
            MdcTraceUtils.withTraceContext(payload.traceContext(), () -> {
                adSearchRepository.save(adDocumentMapper.toDocument(payload));
                if (item.getEventType() == AdSearchOutboxEventType.UPDATED) {
                    adBudgetService.evictCache(payload.id());
                }
            }).run();
            item.markSucceeded();
            log.info("Recovered ES sync outbox. outboxId={}, adId={}", item.getId(), item.getAdId());
        } catch (Exception e) {
            if (item.getAttemptCount() + 1 >= maxAttempts) {
                item.markFailed(e.getMessage());
                log.error("ES sync outbox failed permanently. outboxId={}, adId={}", item.getId(), item.getAdId(), e);
                return;
            }

            long backoffSeconds = Math.min(60L, (long) Math.pow(2, item.getAttemptCount()));
            item.markRetry(e.getMessage(), LocalDateTime.now().plusSeconds(backoffSeconds));
            log.warn("ES sync outbox retry scheduled. outboxId={}, adId={}, nextRetryAt={}",
                    item.getId(), item.getAdId(), item.getNextRetryAt(), e);
        }
    }
}
