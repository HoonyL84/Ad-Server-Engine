package io.hoony.adserver.domain.adevent;

import io.hoony.adserver.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaConfig.class)
class AdEventRepositoryTest {

    @Autowired
    private AdEventRepository adEventRepository;

    @Test
    @DisplayName("eventId 기준으로 같은 이벤트 중복 저장을 막는다.")
    void preventsDuplicateEventId() {
        AdEventRequest request = new AdEventRequest("event-1", "request-1", 101L, "user-1", "home", null);

        adEventRepository.saveAndFlush(AdEvent.of(AdEventType.IMPRESSION, request));

        assertThat(adEventRepository.existsByEventId("event-1")).isTrue();
        assertThatThrownBy(() -> adEventRepository.saveAndFlush(AdEvent.of(AdEventType.IMPRESSION, request)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
