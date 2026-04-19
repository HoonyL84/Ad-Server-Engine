package io.hoony.adserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * [Senior Insight] 왜 별도의 Configuration 클래스를 만드는가?
 * 1. 세밀한 제어: 연결 타임아웃, 소켓 타임아웃, 헤더 설정 등 application.yml만으로는 한계가 있는 설정을 제어합니다.
 * 2. 확장성: 향후 프록시 설정이나 인증(Basic Auth/API Key)이 추가될 때 여기서 한 번에 관리할 수 있습니다.
 * 3. Repository 활성화: ES 전용 Repository가 위치한 패키지를 명시적으로 지정하여 인지 효율을 높입니다.
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "io.hoony.adserver.domain.ad.search")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo("localhost:9200") // 도커로 띄운 ES 주소
                // .withConnectTimeout(Duration.ofSeconds(5)) // 연결 타임아웃 5초
                // .withSocketTimeout(Duration.ofSeconds(3))  // 소켓 타임아웃 3초
                .build();
    }
}
