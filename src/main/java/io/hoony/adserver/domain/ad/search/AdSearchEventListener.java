package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.Ad;
import io.hoony.adserver.domain.ad.event.AdCreatedEvent;
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

    private final AdSearchRepository adSearchRepository;
    private final AdDocumentMapper adDocumentMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAdCreatedEvent(AdCreatedEvent event) {
        Ad ad = event.getAd();
        log.info("Starting ES sync for ad: {}", ad.getId());

        try {
            adSearchRepository.save(adDocumentMapper.toDocument(ad));
            log.info("Successfully synced ad: {} to Elasticsearch", ad.getId());
        } catch (Exception e) {
            log.error("Failed to sync ad: {} to Elasticsearch", ad.getId(), e);
        }
    }
}
