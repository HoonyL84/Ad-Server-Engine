package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.event.AdEventPayload;
import io.hoony.adserver.domain.ad.event.AdUpdatedEvent;
import io.hoony.adserver.domain.serving.AdBudgetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdSearchEventListenerTest {

    private final AdSearchRepository adSearchRepository = mock(AdSearchRepository.class);
    private final AdDocumentMapper adDocumentMapper = mock(AdDocumentMapper.class);
    private final AdBudgetService adBudgetService = mock(AdBudgetService.class);
    private final AdSearchOutboxService adSearchOutboxService = mock(AdSearchOutboxService.class);
    private final AdSearchEventListener listener =
            new AdSearchEventListener(adSearchRepository, adDocumentMapper, adBudgetService, adSearchOutboxService);

    @Test
    @DisplayName("Updated ad event evicts budget cache only after ES sync succeeds")
    void evictsBudgetCacheAfterUpdatedAdIsSyncedToElasticsearch() {
        AdEventPayload payload = mock(AdEventPayload.class);
        AdDocument document = mock(AdDocument.class);

        when(payload.id()).thenReturn(1L);
        when(adDocumentMapper.toDocument(payload)).thenReturn(document);
        when(adSearchRepository.save(document)).thenReturn(document);

        listener.handleAdUpdatedEvent(new AdUpdatedEvent(payload));

        verify(adSearchRepository).save(document);
        verify(adBudgetService).evictCache(1L);
        verify(adSearchOutboxService, never()).enqueue(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("Updated ad event keeps budget cache when ES sync fails")
    void doesNotEvictBudgetCacheWhenUpdatedAdSyncFails() {
        AdEventPayload payload = mock(AdEventPayload.class);
        AdDocument document = mock(AdDocument.class);

        when(payload.id()).thenReturn(1L);
        when(adDocumentMapper.toDocument(payload)).thenReturn(document);
        when(adSearchRepository.save(document)).thenThrow(new IllegalStateException("ES down"));

        listener.handleAdUpdatedEvent(new AdUpdatedEvent(payload));

        verify(adBudgetService, never()).evictCache(1L);
        verify(adSearchOutboxService).enqueue(
                org.mockito.ArgumentMatchers.eq(AdSearchOutboxEventType.UPDATED),
                org.mockito.ArgumentMatchers.eq(payload),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
