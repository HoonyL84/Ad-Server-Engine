package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
public class RedisAdBudgetService implements AdBudgetService {

    private static final String KEY_PREFIX = "ad:budget:";
    private static final long IMPRESSION_CHARGE_WON = 10L;
    private static final long EXHAUSTED_TTL_SECONDS = 300L;

    private static final DefaultRedisScript<Long> SPEND_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            local limit = tonumber(ARGV[2])
            local cost = tonumber(ARGV[1])
            local exhaustedTtl = ARGV[3]
            local timeRatio = tonumber(ARGV[4])
            local randomSample = tonumber(ARGV[5])

            if current then
                current = tonumber(current)
            else
                current = limit
            end

            local actualSpend = limit - current
            if actualSpend > 0 then
                local expectedSpend = limit * timeRatio
                if actualSpend > expectedSpend then
                    local pacingFactor = expectedSpend / actualSpend
                    if randomSample >= pacingFactor then
                        return -2
                    end
                end
            end

            if current < cost then
                redis.call('SET', KEYS[2], '1', 'EX', exhaustedTtl)
                return -1
            end

            local remaining = current - cost
            redis.call('SET', KEYS[1], remaining, 'EX', 86400)

            if remaining < cost then
                redis.call('SET', KEYS[2], '1', 'EX', exhaustedTtl)
            end

            return remaining
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final Random random;

    @Autowired
    public RedisAdBudgetService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Clock.systemDefaultZone(), new Random());
    }

    RedisAdBudgetService(StringRedisTemplate redisTemplate, Clock clock, Random random) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.random = random;
    }

    @Override
    public boolean trySpend(AdDocument ad) {
        long cost = IMPRESSION_CHARGE_WON;
        long initialRemaining = toWon(ad.remainingBudget());

        if (cost <= 0 || initialRemaining <= 0) {
            log.debug("Skip budget spend because document remaining budget is empty. adId={}", ad.getId());
            return false;
        }

        Long remaining = redisTemplate.execute(
                SPEND_SCRIPT,
                List.of(remainingKey(ad), exhaustedKey(ad)),
                String.valueOf(cost),
                String.valueOf(initialRemaining),
                String.valueOf(EXHAUSTED_TTL_SECONDS),
                String.valueOf(timeRatio()),
                String.valueOf(random.nextDouble())
        );

        return remaining != null && remaining >= 0;
    }

    @Override
    public boolean isExhausted(AdDocument ad) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(exhaustedKey(ad)));
    }

    @Override
    public void evictCache(Long adId) {
        redisTemplate.delete(remainingKeyById(adId));
        redisTemplate.delete(exhaustedKeyById(adId));
        log.info("Evicted budget cache for adId={}", adId);
    }

    @Override
    public List<AdDocument> filterExhausted(List<AdDocument> ads) {
        if (ads == null || ads.isEmpty()) {
            return List.of();
        }

        List<String> keys = ads.stream().map(this::exhaustedKey).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);

        if (values == null) {
            return ads;
        }

        List<AdDocument> result = new java.util.ArrayList<>();
        for (int i = 0; i < ads.size(); i++) {
            if (i < values.size() && values.get(i) == null) {
                result.add(ads.get(i));
            }
        }
        return result;
    }

    private double timeRatio() {
        LocalTime nowTime = LocalTime.now(clock);
        return (nowTime.getHour() * 60 + nowTime.getMinute()) / 1440.0;
    }

    private String remainingKey(AdDocument ad) {
        return remainingKeyById(ad.getId());
    }

    private String exhaustedKey(AdDocument ad) {
        return exhaustedKeyById(ad.getId());
    }

    private String remainingKeyById(Long adId) {
        return KEY_PREFIX + adId + ":remaining";
    }

    private String exhaustedKeyById(Long adId) {
        return KEY_PREFIX + adId + ":exhausted";
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
