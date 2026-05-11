package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAdBudgetServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RedisAdBudgetService service = new RedisAdBudgetService(redisTemplate);

    @Test
    @DisplayName("Redis 차감 결과가 0 이상이면 예산 사용 성공으로 본다.")
    void returnsTrueWhenRedisSpendSucceeds() {
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any()))
                .thenReturn(900L);

        boolean result = service.trySpend(ad("1000", "10000", "0"));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("금액은 원 단위로 HALF_UP 반올림해서 Redis에 전달한다.")
    void roundsMoneyToWonWithHalfUp() {
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any()))
                .thenReturn(900L);

        service.trySpend(ad("1234.567", "10000", "0"));

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                org.mockito.ArgumentMatchers.eq("1235"),
                org.mockito.ArgumentMatchers.eq("10000")
        );
    }

    @Test
    @DisplayName("Redis 차감 결과가 음수이면 예산 부족으로 본다.")
    void returnsFalseWhenRedisSpendFails() {
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), anyList(), any(), any()))
                .thenReturn(-1L);

        boolean result = service.trySpend(ad("1000", "10000", "0"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("남은 예산이 없으면 Redis 호출 없이 실패로 본다.")
    void returnsFalseWhenInitialBudgetIsEmpty() {
        boolean result = service.trySpend(ad("1000", "10000", "10000"));

        assertThat(result).isFalse();
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
