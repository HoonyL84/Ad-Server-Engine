package io.hoony.adserver.domain.adstatistic;

import io.hoony.adserver.config.TracingSupport;
import io.hoony.adserver.domain.ad.AdRepository;
import io.hoony.adserver.domain.adevent.AdEventRepository;
import io.hoony.adserver.domain.adevent.AdEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdStatisticService {

    private static final int REALIGN_CHUNK_SIZE = 500;

    private final AdStatisticRepository adStatisticRepository;
    private final AdRepository adRepository;
    private final StringRedisTemplate redisTemplate;
    private final AdEventRepository adEventRepository;
    private final TracingSupport tracingSupport;

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

    public Map<Long, AdStatisticDto> getStatistics(List<Long> adIds) {
        if (adIds == null || adIds.isEmpty()) {
            return Map.of();
        }

        List<String> keys = buildStatKeys(adIds);
        List<String> values = tracingSupport.observe("ad.redis.stat.multiget", () ->
                redisTemplate.opsForValue().multiGet(keys));
        Map<Long, AdStatisticDto> result = new HashMap<>();
        List<Long> missingAdIds = new ArrayList<>();
        List<Integer> missingIndices = new ArrayList<>();

        for (int i = 0; i < adIds.size(); i++) {
            Long adId = adIds.get(i);
            String impVal = valueAt(values, 2 * i);
            String clkVal = valueAt(values, 2 * i + 1);

            if (impVal == null || clkVal == null) {
                missingAdIds.add(adId);
                missingIndices.add(i);
            } else {
                result.put(adId, new AdStatisticDto(
                        parseLongOrDefault(impVal, 0L),
                        parseLongOrDefault(clkVal, 0L)
                ));
            }
        }

        if (!missingAdIds.isEmpty()) {
            Map<Long, AdStatistic> dbStats = adStatisticRepository.findAllById(missingAdIds).stream()
                    .collect(Collectors.toMap(AdStatistic::getAdId, Function.identity()));
            Map<String, String> missingUpdates = new HashMap<>();

            for (int idx : missingIndices) {
                Long adId = adIds.get(idx);
                String impVal = valueAt(values, 2 * idx);
                String clkVal = valueAt(values, 2 * idx + 1);

                AdStatistic dbStat = dbStats.get(adId);
                long imp = impVal != null
                        ? parseLongOrDefault(impVal, 0L)
                        : (dbStat != null ? dbStat.getImpressionCount() : 0L);
                long clk = clkVal != null
                        ? parseLongOrDefault(clkVal, 0L)
                        : (dbStat != null ? dbStat.getClickCount() : 0L);

                result.put(adId, new AdStatisticDto(imp, clk));

                // 기존 Redis 카운터가 있는 키는 덮어쓰지 않고, 누락된 키만 단일 multiSet 배치로 적재한다.
                if (impVal == null) {
                    missingUpdates.put(impKey(adId), String.valueOf(imp));
                }
                if (clkVal == null) {
                    missingUpdates.put(clkKey(adId), String.valueOf(clk));
                }
            }

            if (!missingUpdates.isEmpty()) {
                tracingSupport.observe("ad.redis.stat.multiset", () ->
                        redisTemplate.opsForValue().multiSet(missingUpdates));
            }
        }

        return result;
    }

    @Scheduled(fixedDelayString = "${ad-server.serving.sync-delay-ms:60000}")
    @Transactional
    public void syncToDatabase() {
        tracingSupport.observe("ad.stat.sync", this::doSyncToDatabase);
    }

    private void doSyncToDatabase() {
        log.debug("Syncing Ad statistics from Redis to Database...");
        List<Long> adIds = adRepository.findAllIds();
        if (adIds.isEmpty()) {
            return;
        }

        Map<Long, AdStatistic> existingStats = adStatisticRepository.findAllById(adIds).stream()
                .collect(Collectors.toMap(AdStatistic::getAdId, Function.identity()));
        Map<Long, AdStatisticDto> redisStats = getRedisStatistics(adIds);
        List<AdStatistic> statsToSave = new ArrayList<>();
        for (Long adId : adIds) {
            AdStatisticDto redisStat = redisStats.get(adId);
            if (redisStat != null) {
                AdStatistic existing = existingStats.getOrDefault(adId, AdStatistic.of(adId, 0L, 0L));
                long impressions = redisStat.impressions();
                long clicks = redisStat.clicks();
                if (impressions < 0) {
                    impressions = existing.getImpressionCount();
                }
                if (clicks < 0) {
                    clicks = existing.getClickCount();
                }
                statsToSave.add(AdStatistic.of(adId, impressions, clicks));
            }
        }

        if (!statsToSave.isEmpty()) {
            adStatisticRepository.saveAll(statsToSave);
            log.info("Successfully synced {} Ad statistics to Database.", statsToSave.size());
        }
    }

    @Scheduled(cron = "${ad-server.serving.realign-cron:0 0 3 * * *}")
    @Transactional
    public void realignStatisticsFromEventLedger() {
        tracingSupport.observe("ad.stat.realign", this::doRealignStatisticsFromEventLedger);
    }

    private void doRealignStatisticsFromEventLedger() {
        log.info("Starting Ad statistics realign batch from event ledger using chunked aggregation...");
        List<Long> adIds = adRepository.findAllIds();
        if (adIds.isEmpty()) {
            return;
        }

        int savedCount = 0;
        for (int start = 0; start < adIds.size(); start += REALIGN_CHUNK_SIZE) {
            List<Long> chunk = adIds.subList(start, Math.min(start + REALIGN_CHUNK_SIZE, adIds.size()));
            savedCount += realignChunk(chunk);
        }

        log.info("Successfully realigned {} Ad statistics from event ledger.", savedCount);
    }

    private int realignChunk(List<Long> adIds) {
        List<AdEventCountDto> aggregated = adEventRepository.countEventsGroupedByAdIds(adIds);
        Map<Long, Map<AdEventType, Long>> eventMap = aggregated.stream()
                .collect(Collectors.groupingBy(
                        AdEventCountDto::adId,
                        Collectors.toMap(AdEventCountDto::eventType, AdEventCountDto::count, (v1, v2) -> v1)
                ));

        List<AdStatistic> statsToSave = new ArrayList<>();
        Map<String, String> redisUpdates = new HashMap<>();
        for (Long adId : adIds) {
            Map<AdEventType, Long> counts = eventMap.getOrDefault(adId, Map.of());
            long realImp = counts.getOrDefault(AdEventType.IMPRESSION, 0L);
            long realClk = counts.getOrDefault(AdEventType.CLICK, 0L);

            redisUpdates.put("ad:stat:imp:" + adId, String.valueOf(realImp));
            redisUpdates.put("ad:stat:clk:" + adId, String.valueOf(realClk));

            statsToSave.add(AdStatistic.of(adId, realImp, realClk));
        }

        if (!statsToSave.isEmpty()) {
            tracingSupport.observe("ad.redis.stat.multiset", () ->
                    redisTemplate.opsForValue().multiSet(redisUpdates));
            adStatisticRepository.saveAll(statsToSave);
        }
        return statsToSave.size();
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

    private Map<Long, AdStatisticDto> getRedisStatistics(List<Long> adIds) {
        List<String> keys = buildStatKeys(adIds);

        List<String> values = tracingSupport.observe("ad.redis.stat.multiget", () ->
                redisTemplate.opsForValue().multiGet(keys));
        if (values == null) {
            return Map.of();
        }

        Map<Long, AdStatisticDto> result = new HashMap<>();
        for (int i = 0; i < adIds.size(); i++) {
            String impVal = valueAt(values, 2 * i);
            String clkVal = valueAt(values, 2 * i + 1);
            if (impVal == null && clkVal == null) {
                continue;
            }

            long impressions = impVal != null ? parseLongOrDefault(impVal, 0L) : -1L;
            long clicks = clkVal != null ? parseLongOrDefault(clkVal, 0L) : -1L;
            result.put(adIds.get(i), new AdStatisticDto(impressions, clicks));
        }
        return result;
    }

    private List<String> buildStatKeys(List<Long> adIds) {
        List<String> keys = new ArrayList<>(adIds.size() * 2);
        for (Long adId : adIds) {
            keys.add(impKey(adId));
            keys.add(clkKey(adId));
        }
        return keys;
    }

    private String impKey(Long adId) {
        return "ad:stat:imp:" + adId;
    }

    private String clkKey(Long adId) {
        return "ad:stat:clk:" + adId;
    }

    private String valueAt(List<String> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }
}
