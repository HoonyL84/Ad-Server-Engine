package io.hoony.adserver.domain.ad.event;

import io.hoony.adserver.domain.ad.Ad;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * [Senior Insight] 이벤트를 통한 결합도 낮추기
 * 광고가 생성되었다는 사실을 전파하는 '메신저' 역할을 합니다.
 * 서비스 레이어에서 직접 ES 레포지토리를 호출하지 않고 이벤트를 던짐으로써, 
 * 향후 Kafka 도입이나 또 다른 부가 기능을 추가할 때 기존 코드 수정 없이 확장할 수 있습니다.
 */
@Getter
@RequiredArgsConstructor
public class AdCreatedEvent {
    private final Ad ad;
}
