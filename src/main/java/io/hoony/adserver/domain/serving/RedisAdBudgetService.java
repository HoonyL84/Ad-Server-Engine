package io.hoony.adserver.domain.serving;

import io.hoony.adserver.config.TracingSupport;
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

    // Redis 안에서 조회/판단/차감을 한 번에 끝내 예산 중복 차감을 막는다.
    private static final DefaultRedisScript<Long> SPEND_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            local limit = tonumber(ARGV[2])
            local cost = tonumber(ARGV[1])
            local exhaustedTtl = ARGV[3]
            local timeRatio = tonumber(ARGV[4])
            local randomSample = tonumber(ARGV[5])

            -- Redis에 잔액 키가 없으면 아직 차감 상태가 없는 첫 요청으로 보고 문서 기준 잔액에서 시작한다.
            if current then
                current = tonumber(current)
            else
                current = limit
            end

            -- 단순한 휴리스틱 pacing: 시간 대비 예산을 너무 빨리 쓰면 일부 요청을 확률적으로 건너뛴다.
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

            -- 이번 노출 비용도 감당하지 못하면 소진 마커를 찍고 실패로 본다.
            if current < cost then
                redis.call('SET', KEYS[2], '1', 'EX', exhaustedTtl)
                return -1
            end

            local remaining = current - cost
            redis.call('SET', KEYS[1], remaining, 'EX', 86400)

            -- 이번 노출은 통과했지만 다음 1회분도 부족하면 이후 후보 필터에서 빠지도록 표시한다.
            if remaining < cost then
                redis.call('SET', KEYS[2], '1', 'EX', exhaustedTtl)
            end

            return remaining
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final TracingSupport tracingSupport;
    private final Clock clock;
    private final Random random;

    @Autowired
    public RedisAdBudgetService(StringRedisTemplate redisTemplate, TracingSupport tracingSupport) {
        this(redisTemplate, tracingSupport, Clock.systemDefaultZone(), new Random());
    }

    RedisAdBudgetService(StringRedisTemplate redisTemplate, TracingSupport tracingSupport, Clock clock, Random random) {
        this.redisTemplate = redisTemplate;
        this.tracingSupport = tracingSupport;
        this.clock = clock;
        this.random = random;
    }

    @Override
    public boolean trySpend(AdDocument ad) {
        long cost = IMPRESSION_CHARGE_WON;
        // Redis 초기 동기화가 없으므로 ES 문서의 남은 예산을 첫 차감 기준으로 사용한다.
        long initialRemaining = toWon(ad.remainingBudget());

        if (cost <= 0 || initialRemaining <= 0) {
            log.debug("Skip budget spend because document remaining budget is empty. adId={}", ad.getId());
            return false;
        }

        Long remaining = tracingSupport.observe("ad.redis.budget.spend", () ->
                redisTemplate.execute(
                        SPEND_SCRIPT,
                        List.of(remainingKey(ad), exhaustedKey(ad)),
                        String.valueOf(cost),
                        String.valueOf(initialRemaining),
                        String.valueOf(EXHAUSTED_TTL_SECONDS),
                        String.valueOf(timeRatio()),
                        String.valueOf(random.nextDouble())
                ));

        return remaining != null && remaining >= 0;
    }

    @Override
    public boolean isExhausted(AdDocument ad) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(exhaustedKey(ad)));
    }

    @Override
    public void evictCache(Long adId) {
        // 광고 업데이트 흐름이 실제로 탈 때만 기존 Redis 예산 상태를 버리고 새 문서 기준으로 다시 시작한다.
        redisTemplate.delete(remainingKeyById(adId));
        redisTemplate.delete(exhaustedKeyById(adId));
        log.info("Evicted budget cache for adId={}", adId);
    }

    @Override
    public List<AdDocument> filterExhausted(List<AdDocument> ads) {
        if (ads == null || ads.isEmpty()) {
            return List.of();
        }

        // 후보마다 단건 조회하지 않고 MGET으로 소진 마커를 한 번에 확인한다.
        List<String> keys = ads.stream().map(this::exhaustedKey).toList();
        List<String> values = tracingSupport.observe("ad.redis.budget.exhausted", () ->
                redisTemplate.opsForValue().multiGet(keys));

        if (values == null) {
            return ads;
        }

        List<AdDocument> result = new java.util.ArrayList<>();
        for (int i = 0; i < ads.size(); i++) {
            if (i < values.size() && values.get(i) == null) {
                // 값이 null이면 소진 마커가 없다는 뜻이므로 예산 차감 후보로 남긴다.
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

