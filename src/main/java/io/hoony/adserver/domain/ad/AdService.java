package io.hoony.adserver.domain.ad;

import io.hoony.adserver.domain.ad.event.AdCreatedEvent;
import io.hoony.adserver.domain.ad.event.AdEventPayload;
import io.hoony.adserver.domain.ad.event.AdUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdService {

    private final AdRepository adRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long createAd(Ad ad) {
        log.info("Creating new ad: {}", ad.getTitle());
        Ad savedAd = adRepository.save(ad);
        eventPublisher.publishEvent(new AdCreatedEvent(AdEventPayload.from(savedAd)));
        return savedAd.getId();
    }

    @Transactional
    public void updateBudget(Long adId, BigDecimal newTotalBudget) {
        log.info("Updating budget for adId={}: newTotalBudget={}", adId, newTotalBudget);
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new IllegalArgumentException("Ad not found: " + adId));
        ad.updateTotalBudget(newTotalBudget);
        adRepository.save(ad);

        eventPublisher.publishEvent(new AdUpdatedEvent(AdEventPayload.from(ad)));
    }
}
