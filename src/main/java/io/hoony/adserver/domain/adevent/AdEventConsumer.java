package io.hoony.adserver.domain.adevent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdEventConsumer {

    private final AdEventRepository adEventRepository;

    @KafkaListener(topics = "ad-impressions", groupId = "ad-server-group")
    public void consumeImpression(AdEventRequest request) {
        log.debug("Consuming impression event from Kafka: eventId={}", request.eventId());
        saveEvent(AdEventType.IMPRESSION, request);
    }

    @KafkaListener(topics = "ad-clicks", groupId = "ad-server-group")
    public void consumeClick(AdEventRequest request) {
        log.debug("Consuming click event from Kafka: eventId={}", request.eventId());
        saveEvent(AdEventType.CLICK, request);
    }

    private void saveEvent(AdEventType eventType, AdEventRequest request) {
        try {
            adEventRepository.save(AdEvent.of(eventType, request));
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate event ignored in consumer: eventId={}", request.eventId());
        } catch (Exception e) {
            log.error("Failed to save event in consumer: eventId={}, error={}", request.eventId(), e.getMessage(), e);
        }
    }
}
