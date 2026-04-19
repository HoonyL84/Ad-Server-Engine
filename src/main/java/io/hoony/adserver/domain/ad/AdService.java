package io.hoony.adserver.domain.ad;

import io.hoony.adserver.domain.ad.event.AdCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Senior Insight] 비즈니스 오케스트레이터의 역할
 * AdService는 단순히 DB에 저장하는 것을 넘어, 이후의 후속 작업(ES 동기화, 알림 등)을 
 * 조율하는 역할을 합니다. Spring의 ApplicationEventPublisher를 통해 생성 사실을 전파합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdService {

    private final AdRepository adRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 광고를 저장하고 '생성 이벤트'를 발행합니다.
     * @Transactional이 걸려있으므로, DB 저장이 성공해야만 다음 단계로 앱 수준 이벤트가 전달됩니다.
     */
    @Transactional
    public Long createAd(Ad ad) {
        log.info("Creating new ad: {}", ad.getTitle());
        Ad savedAd = adRepository.save(ad);

        // [Event Publish] "광고가 생성되었다"는 소식을 시스템에 뿌립니다.
        // 이 소식은 AdSearchEventListener가 받게 됩니다.
        eventPublisher.publishEvent(new AdCreatedEvent(savedAd));

        return savedAd.getId();
    }
}
