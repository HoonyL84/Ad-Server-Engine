package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisAdBudgetService implements AdBudgetService {

    private static final String KEY_PREFIX = "ad:budget:";
    private static final long IMPRESSION_CHARGE_WON = 10L;
    private static final long EXHAUSTED_TTL_SECONDS = 300L;
    // Redis 안에서 잔액 확인과 차감을 한 번에 처리해 동시 요청의 중복 차감을 줄인다.
    private static final DefaultRedisScript<Long> SPEND_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
                current = ARGV[2]
            end

            current = tonumber(current)
            local cost = tonumber(ARGV[1])

            if current < cost then
                redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
                return -1
            end

            local remaining = current - cost
            redis.call('SET', KEYS[1], remaining)

            if remaining < cost then
                redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
            end

            return remaining
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean trySpend(AdDocument ad) {
        long cost = IMPRESSION_CHARGE_WON;
        long initialRemaining = toWon(ad.remainingBudget());

        if (cost <= 0 || initialRemaining <= 0) {
            markExhausted(ad);
            return false;
        }

        Long remaining = redisTemplate.execute(
                SPEND_SCRIPT,
                List.of(remainingKey(ad), exhaustedKey(ad)),
                String.valueOf(cost),
                String.valueOf(initialRemaining),
                String.valueOf(EXHAUSTED_TTL_SECONDS)
        );

        return remaining != null && remaining >= 0;
    }

    @Override
    public boolean isExhausted(AdDocument ad) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(exhaustedKey(ad)));
    }

    private void markExhausted(AdDocument ad) {
        redisTemplate.opsForValue().set(
                exhaustedKey(ad),
                "1",
                Duration.ofSeconds(EXHAUSTED_TTL_SECONDS)
        );
    }

    private String remainingKey(AdDocument ad) {
        return KEY_PREFIX + ad.getId() + ":remaining";
    }

    private String exhaustedKey(AdDocument ad) {
        return KEY_PREFIX + ad.getId() + ":exhausted";
    }

    private long toWon(BigDecimal value) {
        if (value == null) {
            return 0L;
        }
        return value
                .max(BigDecimal.ZERO)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }
}
