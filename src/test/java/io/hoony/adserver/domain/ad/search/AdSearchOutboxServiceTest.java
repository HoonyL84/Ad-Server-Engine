package io.hoony.adserver.domain.ad.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.event.AdEventPayload;
import io.hoony.adserver.domain.serving.AdBudgetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdSearchOutboxServiceTest {

    private final AdSearchOutboxRepository outboxRepository = mock(AdSearchOutboxRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdSearchRepository adSearchRepository = mock(AdSearchRepository.class);
    private final AdDocumentMapper adDocumentMapper = mock(AdDocumentMapper.class);
    private final AdBudgetService adBudgetService = mock(AdBudgetService.class);
    private final AdSearchOutboxService service = new AdSearchOutboxService(
            outboxRepository,
            objectMapper,
            adSearchRepository,
            adDocumentMapper,
            adBudgetService
    );

    @Test
    @DisplayName("ES sync 실패 payload를 outbox에 저장한다")
    void enqueueFailedPayload() {
        AdEventPayload payload = payload();

        service.enqueue(AdSearchOutboxEventType.UPDATED, payload, "ES down");

        verify(outboxRepository).save(any(AdSearchOutbox.class));
    }

    @Test
    @DisplayName("outbox 재처리에 성공하면 ES 저장 후 예산 캐시를 무효화한다")
    void retryPendingOutbox() throws Exception {
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        AdEventPayload payload = payload();
        AdSearchOutbox item = AdSearchOutbox.pending(
                payload.id(),
                AdSearchOutboxEventType.UPDATED,
                objectMapper.writeValueAsString(payload),
                "ES down"
        );
        AdDocument document = mock(AdDocument.class);

        when(outboxRepository.findTop50ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(
                any(),
                any(LocalDateTime.class)
        )).thenReturn(List.of(item));
        when(adDocumentMapper.toDocument(any(AdEventPayload.class))).thenReturn(document);
        when(adSearchRepository.save(document)).thenReturn(document);

        service.retryPending();

        verify(adSearchRepository).save(document);
        verify(adBudgetService).evictCache(payload.id());
    }

    private AdEventPayload payload() {
        return new AdEventPayload(
                1L,
                10L,
                "ad",
                "image",
                "https://example.com",
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(100000),
                BigDecimal.ZERO,
                AdStatus.ACTIVE,
                "ALL",
                "0",
                "",
                Map.of(),
                Map.of("traceId", "test-trace")
        );
    }
}
