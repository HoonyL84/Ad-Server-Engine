package io.hoony.adserver.domain.adevent;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Instant;

@Slf4j
@Configuration
public class AdEventKafkaConfig {

    private static final String IMPRESSION_TOPIC = "ad-impressions";
    private static final String CLICK_TOPIC = "ad-clicks";
    private static final String DLQ_TOPIC = "ad-events-dlq";

    @Bean
    public NewTopic impressionTopic() {
        return TopicBuilder.name(IMPRESSION_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic clickTopic() {
        return TopicBuilder.name(CLICK_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic adEventDlqTopic() {
        return TopicBuilder.name(DLQ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(adEventErrorHandler(kafkaTemplate));
        return factory;
    }

    private DefaultErrorHandler adEventErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        return new DefaultErrorHandler((record, exception) -> {
            AdEventDeadLetterPayload payload = toDeadLetterPayload(record, exception);
            kafkaTemplate.send(DLQ_TOPIC, payload.eventId(), payload);
            log.error("Ad event moved to DLQ. eventId={}, topic={}, reason={}",
                    payload.eventId(), payload.sourceTopic(), payload.failureReason());
        }, new FixedBackOff(500L, 2L));
    }

    private AdEventDeadLetterPayload toDeadLetterPayload(ConsumerRecord<?, ?> record, Exception exception) {
        AdEventType eventType = CLICK_TOPIC.equals(record.topic()) ? AdEventType.CLICK : AdEventType.IMPRESSION;
        AdEventRequest request = record.value() instanceof AdEventRequest eventRequest ? eventRequest : null;
        String eventId = request != null ? request.eventId() : String.valueOf(record.key());
        String reason = exception.getCause() != null
                ? exception.getCause().getClass().getSimpleName() + ": " + exception.getCause().getMessage()
                : exception.getClass().getSimpleName() + ": " + exception.getMessage();

        return new AdEventDeadLetterPayload(
                eventId,
                record.topic(),
                eventType,
                request,
                reason,
                Instant.now()
        );
    }
}
