package io.hoony.adserver.domain.user.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FaultInjectingUserProfileClientTest {

    @Test
    @DisplayName("주입 비활성화 시 delegate 결과를 반환한다.")
    void returnsDelegateResultWhenInjectionDisabled() throws Exception {
        RedisUserProfileClient delegate = mock(RedisUserProfileClient.class);
        UserProfile expected = new UserProfile("1", "M", "1:11", 29, List.of("fashion"));
        when(delegate.getUserProfile("1")).thenReturn(Optional.of(expected));

        FaultInjectingUserProfileClient client = new FaultInjectingUserProfileClient(delegate);
        setField(client, "enabled", false);

        Optional<UserProfile> result = client.getUserProfile("1");

        assertThat(result).contains(expected);
    }

    @Test
    @DisplayName("error- prefix는 예외를 발생시킨다.")
    void throwsErrorWhenErrorPrefixIsUsed() throws Exception {
        RedisUserProfileClient delegate = mock(RedisUserProfileClient.class);
        FaultInjectingUserProfileClient client = new FaultInjectingUserProfileClient(delegate);
        setField(client, "enabled", true);

        assertThatThrownBy(() -> client.getUserProfile("error-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Injected DMP error");
    }

    @Test
    @DisplayName("empty- prefix는 Optional.empty를 반환한다.")
    void returnsEmptyWhenEmptyPrefixIsUsed() throws Exception {
        RedisUserProfileClient delegate = mock(RedisUserProfileClient.class);
        FaultInjectingUserProfileClient client = new FaultInjectingUserProfileClient(delegate);
        setField(client, "enabled", true);

        Optional<UserProfile> result = client.getUserProfile("empty-1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("slow- prefix는 지연 후 delegate를 호출한다.")
    void delaysWhenSlowPrefixIsUsed() throws Exception {
        RedisUserProfileClient delegate = mock(RedisUserProfileClient.class);
        when(delegate.getUserProfile("slow-1"))
                .thenReturn(Optional.of(new UserProfile("slow-1", "M", "1:11", 29, List.of())));

        FaultInjectingUserProfileClient client = new FaultInjectingUserProfileClient(delegate);
        setField(client, "enabled", true);
        setField(client, "slowDelayMs", 50L);

        assertTimeoutPreemptively(Duration.ofMillis(300), () -> {
            Optional<UserProfile> result = client.getUserProfile("slow-1");
            assertThat(result).isPresent();
        });
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
