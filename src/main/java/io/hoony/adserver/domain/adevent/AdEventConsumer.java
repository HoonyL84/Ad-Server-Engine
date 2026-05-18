package io.hoony.adserver.domain.adevent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdEventConsumer {

    private final AdEventRepository adEventRepository;
    private final StringRedisTemplate redisTemplate;

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
            incrementRedisCounter(eventType, request.adId());
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate event ignored in consumer: eventId={}", request.eventId());
        } catch (Exception e) {
            log.error("Failed to save event in consumer: eventId={}, error={}", request.eventId(), e.getMessage(), e);
        }
    }

    private void incrementRedisCounter(AdEventType eventType, Long adId) {
        String key = (eventType == AdEventType.IMPRESSION) ?
                "ad:stat:imp:" + adId : "ad:stat:clk:" + adId;
        try {
            redisTemplate.opsForValue().increment(key);
            log.debug("Successfully incremented Redis key={}", key);
        } catch (Exception e) {
            log.error("Failed to increment Redis counter for key: {}, error={}", key, e.getMessage(), e);
        }
    }
}
