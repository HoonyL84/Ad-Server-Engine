package io.hoony.adserver.domain.adevent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdEventService {

    private static final String EVENT_DUP_KEY_PREFIX = "event:dup:";

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AdEventMetrics adEventMetrics;

    public AdEventResult collect(AdEventType eventType, AdEventRequest request) {
        String dupKey = EVENT_DUP_KEY_PREFIX + request.eventId();

        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dupKey, "1", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isNew)) {
            log.debug("Duplicate event detected by Redis: eventId={}", request.eventId());
            adEventMetrics.record(eventType, request.slotId(), true);
            return new AdEventResult(request.eventId(), eventType, true);
        }

        try {
            String topic = eventType == AdEventType.IMPRESSION ? "ad-impressions" : "ad-clicks";
            CompletableFuture<SendResult<String, Object>> sendResult =
                    kafkaTemplate.send(topic, request.eventId(), request);

            sendResult.whenComplete((result, exception) -> {
                if (exception == null) {
                    return;
                }

                log.error("Failed to publish event to Kafka: eventId={}, error={}",
                        request.eventId(), exception.getMessage(), exception);
                adEventMetrics.recordFailure(eventType, request.slotId());
                redisTemplate.delete(dupKey);
            });

            adEventMetrics.record(eventType, request.slotId(), false);
            return new AdEventResult(request.eventId(), eventType, false);
        } catch (Exception e) {
            log.error("Failed to send event to Kafka: eventId={}, error={}", request.eventId(), e.getMessage(), e);
            adEventMetrics.recordFailure(eventType, request.slotId());
            redisTemplate.delete(dupKey);
            throw new RuntimeException("Kafka publish failure", e);
        }
    }
}
