package io.hoony.adserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "io.hoony.adserver.domain.ad.search")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private final String elasticsearchUris;

    public ElasticsearchConfig(@Value("${spring.elasticsearch.uris}") String elasticsearchUris) {
        this.elasticsearchUris = elasticsearchUris;
    }

    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo(elasticsearchUris.replace("http://", "").replace("https://", ""))
                .build();
    }
}
