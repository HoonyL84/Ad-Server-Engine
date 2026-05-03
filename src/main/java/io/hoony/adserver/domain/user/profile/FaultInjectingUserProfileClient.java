package io.hoony.adserver.domain.user.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Primary
@Component
@RequiredArgsConstructor
public class FaultInjectingUserProfileClient implements UserProfileClient {

    private final RedisUserProfileClient delegate;

    @Value("${ad-server.dmp.failure-injection.enabled:false}")
    private boolean enabled;

    @Value("${ad-server.dmp.failure-injection.slow-delay-ms:200}")
    private long slowDelayMs;

    @Override
    public Optional<UserProfile> getUserProfile(String userId) {
        if (!enabled) {
            return delegate.getUserProfile(userId);
        }

        if (userId.startsWith("error-")) {
            throw new IllegalStateException("Injected DMP error for userId=" + userId + " [forced]");
        }

        if (userId.startsWith("empty-")) {
            return Optional.empty();
        }

        if (userId.startsWith("slow-")) {
            sleep(slowDelayMs);
        }

        return delegate.getUserProfile(userId);
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Injected delay interrupted", e);
        }
    }
}
