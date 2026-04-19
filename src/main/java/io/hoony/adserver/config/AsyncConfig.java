package io.hoony.adserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * [Senior Insight] Java 21 가상 스레드(Virtual Threads)의 위력
 * 비동기 처리를 위해 예전처럼 복잡한 스레드 풀(Thread Pool) 설정이 필요 없습니다.
 * Executors.newVirtualThreadPerTaskExecutor()를 사용하면, 
 * 각 비동기 작업마다 가벼운 가상 스레드를 할당하여 I/O 차단 시 리소스를 효율적으로 재사용합니다.
 */
@Configuration
@EnableAsync // @Async 어노테이션을 활성화합니다.
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        // 모든 비동기 작업을 가상 스레드에서 실행하도록 설정
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
