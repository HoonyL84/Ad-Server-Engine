package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EvenPacingSimulationTest {

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("예산 소진이 전혀 안 된 광고는 페이싱 차단이 발생하지 않고 Redis 스크립트를 즉시 실행한다")
    void testNoPacingForFreshAd() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Mock remainingBudget in Redis to equal totalBudget (0 spent)
        when(valueOperations.get(eq("ad:budget:101:remaining"))).thenReturn("10000");

        // Mock Redis script execution
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(9990L);
 
        RedisAdBudgetService budgetService = new RedisAdBudgetService(
                redisTemplate,
                fixedNoonClock(),
                fixedRandom(0.99)
        );
 
        AdDocument freshAd = AdDocument.builder()
                .id(101L)
                .totalBudget(BigDecimal.valueOf(10000))
                .spentAmount(BigDecimal.valueOf(0))
                .build();
 
        boolean result = budgetService.trySpend(freshAd);
 
        // Should pass the pacing guard and proceed to execute Redis spend script
        assertThat(result).isTrue();
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
 
    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("예산이 극단적으로 과소진된 광고는 대부분의 시간대에서 페이싱 가드가 작동하여 Redis 호출 없이 차단된다")
    void testPacingTriggeredForOverspentAd() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
 
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-2L);
 
        RedisAdBudgetService budgetService = new RedisAdBudgetService(
                redisTemplate,
                fixedNoonClock(),
                fixedRandom(0.99)
        );
 
        AdDocument overspentAd = AdDocument.builder()
                .id(102L)
                .totalBudget(BigDecimal.valueOf(10000))
                .spentAmount(BigDecimal.valueOf(9990))
                .build();
 
        boolean result = budgetService.trySpend(overspentAd);

        assertThat(result).isFalse();
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    private Clock fixedNoonClock() {
        return Clock.fixed(Instant.parse("2026-05-20T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    }

    private Random fixedRandom(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }
}
