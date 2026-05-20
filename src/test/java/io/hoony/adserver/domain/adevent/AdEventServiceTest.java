package io.hoony.adserver.domain.adevent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdEventServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final AdEventMetrics adEventMetrics = new AdEventMetrics(new SimpleMeterRegistry());
    private final AdEventService adEventService = new AdEventService(redisTemplate, kafkaTemplate, adEventMetrics);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("새 이벤트를 캐싱하고 Kafka 발행 확인 후 duplicate=false를 반환한다.")
    void collectsNewEvent() {
        AdEventRequest request = request("event-1");

        when(valueOperations.setIfAbsent(eq("event:dup:event-1"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(kafkaTemplate.send(eq("ad-impressions"), eq("event-1"), eq(request)))
                .thenReturn(CompletableFuture.completedFuture(null));

        AdEventResult result = adEventService.collect(AdEventType.IMPRESSION, request);

        assertThat(result.eventId()).isEqualTo("event-1");
        assertThat(result.eventType()).isEqualTo(AdEventType.IMPRESSION);
        assertThat(result.duplicate()).isFalse();
        verify(valueOperations).setIfAbsent("event:dup:event-1", "1", Duration.ofMinutes(10));
        verify(kafkaTemplate).send(eq("ad-impressions"), eq("event-1"), eq(request));
    }

    @Test
    @DisplayName("이미 수집된 eventId는 duplicate=true로 처리한다.")
    void marksDuplicateEvent() {
        AdEventRequest request = request("event-1");

        when(valueOperations.setIfAbsent(eq("event:dup:event-1"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        AdEventResult result = adEventService.collect(AdEventType.CLICK, request);

        assertThat(result.eventId()).isEqualTo("event-1");
        assertThat(result.eventType()).isEqualTo(AdEventType.CLICK);
        assertThat(result.duplicate()).isTrue();
    }

    @Test
    @DisplayName("Kafka 전송 실패 시 Redis 중복 캐시를 제거하고 예외를 전파한다.")
    void recordsFailureWhenKafkaFails() {
        AdEventRequest request = request("event-1");

        when(valueOperations.setIfAbsent(eq("event:dup:event-1"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenThrow(new RuntimeException("kafka broker down"));

        assertThatThrownBy(() -> adEventService.collect(AdEventType.IMPRESSION, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Kafka publish failure");

        verify(redisTemplate).delete("event:dup:event-1");
    }

    @Test
    @DisplayName("Kafka 발행 결과가 실패하면 Redis 중복 캐시를 제거하고 예외를 전파한다.")
    void evictsDuplicateCacheWhenKafkaPublishFailsAsynchronously() {
        AdEventRequest request = request("event-1");

        when(valueOperations.setIfAbsent(eq("event:dup:event-1"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(kafkaTemplate.send(eq("ad-impressions"), eq("event-1"), eq(request)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker ack failed")));

        assertThatThrownBy(() -> adEventService.collect(AdEventType.IMPRESSION, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Kafka publish failure");

        verify(redisTemplate).delete("event:dup:event-1");
    }

    private AdEventRequest request(String eventId) {
        return new AdEventRequest(eventId, "request-1", 101L, "user-1", "home", null);
    }
}
