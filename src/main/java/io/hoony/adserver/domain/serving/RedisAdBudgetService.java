package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisAdBudgetService implements AdBudgetService {

    private static final String KEY_PREFIX = "ad:budget:";
    private static final DefaultRedisScript<Long> SPEND_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
                current = ARGV[2]
            end

            current = tonumber(current)
            local cost = tonumber(ARGV[1])

            if current < cost then
                return -1
            end

            local remaining = current - cost
            redis.call('SET', KEYS[1], remaining)
            return remaining
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean trySpend(AdDocument ad) {
        long cost = toWon(ad.getMaxBid());
        long initialRemaining = toWon(ad.remainingBudget());

        if (cost <= 0 || initialRemaining <= 0) {
            return false;
        }

        Long remaining = redisTemplate.execute(
                SPEND_SCRIPT,
                List.of(KEY_PREFIX + ad.getId() + ":remaining"),
                String.valueOf(cost),
                String.valueOf(initialRemaining)
        );

        return remaining != null && remaining >= 0;
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
