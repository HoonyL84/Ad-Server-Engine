package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.Ad;
import io.hoony.adserver.domain.ad.event.AdCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * [Senior Insight] 데이터 동기화의 파수꾼 (EventListener)
 * 이 리스너는 DB 트랜잭션이 '성공적으로 끝났을 때만' 동작하여 데이터 불일치를 방지합니다.
 * 또한 @Async를 통해 별도의 가상 스레드에서 돌아가므로 메인 비즈니스 로직을 방해하지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdSearchEventListener {

    private final AdSearchRepository adSearchRepository;

    /**
     * 광고 생성 이벤트를 수신하여 Elasticsearch에 인덱싱합니다.
     * TransactionPhase.AFTER_COMMIT: MySQL 트랜잭션이 커밋된 '직후'에 실행됩니다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAdCreatedEvent(AdCreatedEvent event) {
        Ad ad = event.getAd();
        log.info("Starting ES sync for ad: {}", ad.getId());

        try {
            // [Mapping] 엔티티(MySQL)를 도큐먼트(ES)로 변환
            AdDocument document = AdDocument.builder()
                    .id(ad.getId())
                    .advertiserId(ad.getAdvertiser().getId())
                    .title(ad.getTitle())
                    .maxBid(ad.getMaxBid())
                    .status(ad.getStatus())
                    .targetGender(ad.getTargetGender())
                    .targetLocationId(ad.getTargetLocationId())
                    .interestTags(parseTags(ad.getTargetInterestTags())) // 문자열을 리스트로 변환
                    .targetContext(ad.getTargetContext())
                    .build();

            // [Persistence] ES 전송
            adSearchRepository.save(document);
            log.info("Successfully synced ad: {} to Elasticsearch", ad.getId());
            
        } catch (Exception e) {
            // [Senior Point] 실무에서는 실패 시 재시도(Retry) 로직이나 에러 로그 수집이 매우 중요합니다.
            log.error("Failed to sync ad: {} to Elasticsearch", ad.getId(), e);
        }
    }

    /**
     * 콤마로 구분된 태그 문자열을 리스트로 변환합니다.
     */
    private java.util.List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return java.util.Collections.emptyList();
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }
}
