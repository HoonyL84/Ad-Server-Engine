package io.hoony.adserver.domain.adstatistic;

import io.hoony.adserver.domain.ad.AdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdStatisticService {

    private final AdStatisticRepository adStatisticRepository;
    private final AdRepository adRepository;
    private final StringRedisTemplate redisTemplate;

    public AdStatisticDto getStatistic(Long adId) {
        String impKey = "ad:stat:imp:" + adId;
        String clkKey = "ad:stat:clk:" + adId;

        String impVal = redisTemplate.opsForValue().get(impKey);
        String clkVal = redisTemplate.opsForValue().get(clkKey);

        Optional<AdStatistic> dbStat = Optional.empty();

        if (impVal == null || clkVal == null) {
            dbStat = adStatisticRepository.findById(adId);
        }

        long impressions = impVal != null
                ? parseLongOrDefault(impVal, 0L)
                : dbStat.map(AdStatistic::getImpressionCount).orElse(0L);
        long clicks = clkVal != null
                ? parseLongOrDefault(clkVal, 0L)
                : dbStat.map(AdStatistic::getClickCount).orElse(0L);

        if (impVal == null) {
            redisTemplate.opsForValue().set(impKey, String.valueOf(impressions));
        }
        if (clkVal == null) {
            redisTemplate.opsForValue().set(clkKey, String.valueOf(clicks));
        }

        return new AdStatisticDto(impressions, clicks);
    }

    @Scheduled(fixedDelayString = "${ad-server.serving.sync-delay-ms:60000}")
    @Transactional
    public void syncToDatabase() {
        log.debug("Syncing Ad statistics from Redis to Database...");
        List<Long> adIds = adRepository.findAllIds();
        if (adIds.isEmpty()) {
            return;
        }

        Map<Long, AdStatistic> existingStats = adStatisticRepository.findAllById(adIds).stream()
                .collect(Collectors.toMap(AdStatistic::getAdId, Function.identity()));
        List<AdStatistic> statsToSave = new ArrayList<>();
        for (Long adId : adIds) {
            String impKey = "ad:stat:imp:" + adId;
            String clkKey = "ad:stat:clk:" + adId;

            String impVal = redisTemplate.opsForValue().get(impKey);
            String clkVal = redisTemplate.opsForValue().get(clkKey);

            if (impVal != null || clkVal != null) {
                AdStatistic existing = existingStats.getOrDefault(adId, AdStatistic.of(adId, 0L, 0L));
                long impressions = impVal != null
                        ? parseLongOrDefault(impVal, existing.getImpressionCount())
                        : existing.getImpressionCount();
                long clicks = clkVal != null
                        ? parseLongOrDefault(clkVal, existing.getClickCount())
                        : existing.getClickCount();
                statsToSave.add(AdStatistic.of(adId, impressions, clicks));
            }
        }

        if (!statsToSave.isEmpty()) {
            adStatisticRepository.saveAll(statsToSave);
            log.info("Successfully synced {} Ad statistics to Database.", statsToSave.size());
        }
    }

    private long parseLongOrDefault(String value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse Long from Redis value: {}", value);
            return defaultValue;
        }
    }
}
