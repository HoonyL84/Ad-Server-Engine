package io.hoony.adserver.domain.user.profile;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Optional;

@TestConfiguration
public class TestUserProfileClientConfig {

    @Bean
    @Primary
    public UserProfileClient testUserProfileClient() {
        return userId -> {
            if ("missing".equalsIgnoreCase(userId)) {
                return Optional.empty();
            }
            return Optional.of(new UserProfile(
                    userId,
                    "M",
                    "1:11",
                    29,
                    List.of("fashion", "sports")
            ));
        };
    }
}
