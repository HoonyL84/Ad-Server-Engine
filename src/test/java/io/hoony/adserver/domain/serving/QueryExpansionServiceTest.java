package io.hoony.adserver.domain.serving;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryExpansionServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Redis 캐시 Hit 시 LLM을 호출하지 않고 확장된 카테고리 태그를 즉시 반환한다")
    void returnsCachedTagsImmediatelyOnHit() {
        when(valueOperations.get("ad:query:expansion:캠핑")).thenReturn("캠핑,outdoor,tent,suv");

        QueryExpansionService service = new QueryExpansionService(
                redisTemplate,
                query -> {
                    throw new AssertionError("Should not call LLM on cache hit");
                },
                Runnable::run
        );

        List<String> tags = service.resolveExpandedTags(" 캠핑 ");

        assertThat(tags).containsExactly("캠핑", "outdoor", "tent", "suv");
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 캐시 Miss 시 기본 키워드로 즉시 응답하고 비동기로 LLM 확장 결과를 캐시에 적재한다")
    void returnsBaseQueryOnMissAndPopulatesCacheAsynchronously() {
        when(valueOperations.get("ad:query:expansion:운동화")).thenReturn(null);

        QueryExpansionService service = new QueryExpansionService(
                redisTemplate,
                query -> List.of("running", "sneakers", "sports"),
                Runnable::run
        );

        List<String> immediateTags = service.resolveExpandedTags("운동화");

        assertThat(immediateTags).containsExactly("운동화");
        verify(valueOperations).set(
                eq("ad:query:expansion:운동화"),
                eq("운동화,running,sneakers,sports"),
                eq(Duration.ofHours(24))
        );
    }
}
