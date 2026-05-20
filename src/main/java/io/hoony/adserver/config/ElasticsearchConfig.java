package io.hoony.adserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.time.Duration;

@Configuration
@EnableElasticsearchRepositories(basePackages = "io.hoony.adserver.domain.ad.search")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private final String elasticsearchUris;
    private final Duration connectTimeout;
    private final Duration socketTimeout;

    public ElasticsearchConfig(
            @Value("${spring.elasticsearch.uris}") String elasticsearchUris,
            @Value("${spring.elasticsearch.connection-timeout:100ms}") Duration connectTimeout,
            @Value("${spring.elasticsearch.socket-timeout:150ms}") Duration socketTimeout
    ) {
        this.elasticsearchUris = elasticsearchUris;
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
    }

    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo(elasticsearchUris.replace("http://", "").replace("https://", ""))
                .withConnectTimeout(connectTimeout)
                .withSocketTimeout(socketTimeout)
                .build();
    }
}
