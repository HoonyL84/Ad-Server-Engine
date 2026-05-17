package io.hoony.adserver.domain.adevent;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class AdEventKafkaConfig {

    @Bean
    public NewTopic impressionTopic() {
        return TopicBuilder.name("ad-impressions")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic clickTopic() {
        return TopicBuilder.name("ad-clicks")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
