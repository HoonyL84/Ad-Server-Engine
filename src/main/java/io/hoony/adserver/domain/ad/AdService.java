package io.hoony.adserver.domain.ad;

import io.hoony.adserver.domain.ad.event.AdCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        eventPublisher.publishEvent(new AdCreatedEvent(savedAd));
        return savedAd.getId();
    }
}
