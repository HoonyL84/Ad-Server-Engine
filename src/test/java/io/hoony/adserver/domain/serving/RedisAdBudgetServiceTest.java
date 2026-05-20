package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;

class RedisAdBudgetServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisAdBudgetService service = new RedisAdBudgetService(redisTemplate);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Redis 차감 결과가 0 이상이면 예산 사용 성공으로 본다.")
    void returnsTrueWhenRedisSpendSucceeds() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                eq("10"),
                eq("10000"),
                eq("300"),
                any(),
                any()
        )).thenReturn(900L);

        boolean result = service.trySpend(ad("1000", "10000", "0"));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("서빙 1회당 고정 노출 비용 10원을 Redis에 전달한다.")
    void spendsFixedImpressionCharge() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                any(),
                any(),
                any()
        )).thenReturn(900L);

        service.trySpend(ad("1234.567", "10000", "0"));

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                eq("10"),
                eq("10000"),
                eq("300"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Redis 차감 결과가 음수이면 예산 부족으로 본다.")
    void returnsFalseWhenRedisSpendFails() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                eq("10"),
                eq("10000"),
                eq("300"),
                any(),
                any()
        )).thenReturn(-1L);

        boolean result = service.trySpend(ad("1000", "10000", "0"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("문서 기준 남은 예산이 없으면 Redis 소진 마커를 다시 쓰지 않고 실패로 본다.")
    void returnsFalseWhenInitialBudgetIsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        boolean result = service.trySpend(ad("1000", "10000", "10000"));

        assertThat(result).isFalse();
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("소진 마커가 있으면 예산 소진 상태로 본다.")
    void returnsTrueWhenExhaustedMarkerExists() {
        when(redisTemplate.hasKey("ad:budget:1:exhausted")).thenReturn(true);

        boolean result = service.isExhausted(ad("1000", "10000", "0"));

        assertThat(result).isTrue();
    }

    private AdDocument ad(String bid, String totalBudget, String spentAmount) {
        return AdDocument.builder()
                .id(1L)
                .advertiserId(1L)
                .title("ad")
                .maxBid(new BigDecimal(bid))
                .totalBudget(new BigDecimal(totalBudget))
                .spentAmount(new BigDecimal(spentAmount))
                .status(AdStatus.ACTIVE)
                .targetGender("ALL")
                .targetLocationId("0")
                .interestTags(List.of())
                .build();
    }
}
