package io.hoony.adserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HealthCheckController {

    @GetMapping("/health")
    public String health() {
        String threadName = Thread.currentThread().toString();
        log.info("Health check requested on thread: {}", threadName);
        return "Ad Server Engine is Running! Current Thread: " + threadName;
    }
}
