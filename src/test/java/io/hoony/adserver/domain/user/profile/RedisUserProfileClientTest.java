package io.hoony.adserver.domain.user.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisUserProfileClientTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("comma tags payload를 UserProfile로 변환한다.")
    void returnsProfileWithCommaSeparatedTags() {
        String payload = """
                {"user_id":"1","gender":"M","location_id":"1:11","age":29,"tags":"fashion,sports"}
                """;

        RedisUserProfileClient client = new RedisUserProfileClient(redisTemplate, new ObjectMapper());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:profile:1")).thenReturn(payload);

        Optional<UserProfile> result = client.getUserProfile("1");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo("1");
        assertThat(result.get().gender()).isEqualTo("M");
        assertThat(result.get().locationId()).isEqualTo("1:11");
        assertThat(result.get().age()).isEqualTo(29);
        assertThat(result.get().tags()).containsExactly("fashion", "sports");
    }

    @Test
    @DisplayName("array tags payload를 UserProfile로 변환한다.")
    void returnsProfileWithArrayTags() {
        String payload = """
                {"user_id":"2","gender":"F","location_id":"2:22","age":"31","tags":["living","interior"]}
                """;

        RedisUserProfileClient client = new RedisUserProfileClient(redisTemplate, new ObjectMapper());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:profile:2")).thenReturn(payload);

        Optional<UserProfile> result = client.getUserProfile("2");

        assertThat(result).isPresent();
        assertThat(result.get().age()).isEqualTo(31);
        assertThat(result.get().tags()).containsExactly("living", "interior");
    }

    @Test
    @DisplayName("payload가 없으면 Optional.empty를 반환한다.")
    void returnsEmptyWhenPayloadIsMissing() {
        RedisUserProfileClient client = new RedisUserProfileClient(redisTemplate, new ObjectMapper());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:profile:missing")).thenReturn(null);

        Optional<UserProfile> result = client.getUserProfile("missing");

        assertThat(result).isEmpty();
    }
}
