package io.hoony.adserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AdServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdServerApplication.class, args);
    }
}
