package io.hoony.adserver.domain.serving;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * [모듈 02/03] 검색어 의미 확장(Query Expansion) 서비스.
 * - Hot Path: Redis 캐시 우선 조회(1ms 이내) -> Hit 시 확장 태그 즉시 반환
 * - Cold Path: Cache Miss 시 서빙 지연을 막기 위해 원본 키워드로 즉시 서빙하고,
 *              백그라운드 스레드에서 비동기로 LLM 확장 결과를 Redis에 적재한다.
 */
@Slf4j
@Service
public class QueryExpansionService {

    static final String KEY_PREFIX = "ad:query:expansion:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final LlmQueryExpander llmQueryExpander;
    private final Executor asyncExecutor;
    private final Set<String> inFlightQueries = ConcurrentHashMap.newKeySet();

    @Autowired
    public QueryExpansionService(StringRedisTemplate redisTemplate, Executor executorService) {
        this(redisTemplate, query -> List.of(query), executorService);
    }

    public QueryExpansionService(
            StringRedisTemplate redisTemplate,
            LlmQueryExpander llmQueryExpander,
            Executor asyncExecutor
    ) {
        this.redisTemplate = redisTemplate;
        this.llmQueryExpander = llmQueryExpander;
        this.asyncExecutor = asyncExecutor;
    }

    public List<String> resolveExpandedTags(String rawQuery) {
        String normalizedQuery = normalizeQuery(rawQuery);
        if (normalizedQuery == null) {
            return List.of();
        }

        String cacheKey = KEY_PREFIX + normalizedQuery;

        // 1단계 (Hot Path): Redis 캐시 조회 (1ms 이내)
        String cachedValue = readCacheSafely(cacheKey);
        if (cachedValue != null && !cachedValue.isBlank()) {
            return parseTags(cachedValue);
        }

        // 2단계 (Cold Path): 캐시 미스 시 서빙 스레드는 블로킹하지 않고 비동기 LLM 확장만 트리거
        triggerAsyncExpansion(normalizedQuery, cacheKey);

        // 3단계: 현재 요청은 지연 없이 기본 키워드로 즉시 반환
        return List.of(normalizedQuery);
    }

    private void triggerAsyncExpansion(String normalizedQuery, String cacheKey) {
        if (llmQueryExpander == null || !inFlightQueries.add(normalizedQuery)) {
            return;
        }
        asyncExecutor.execute(() -> {
            try {
                List<String> expanded = llmQueryExpander.expandQueryToTags(normalizedQuery);
                List<String> cleaned = sanitizeTags(normalizedQuery, expanded);
                if (!cleaned.isEmpty()) {
                    redisTemplate.opsForValue().set(cacheKey, String.join(",", cleaned), CACHE_TTL);
                }
            } catch (Exception e) {
                log.warn("Async LLM query expansion failed. query={}", normalizedQuery, e);
            } finally {
                inFlightQueries.remove(normalizedQuery);
            }
        });
    }

    private String readCacheSafely(String cacheKey) {
        try {
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis query expansion cache read failed. key={}", cacheKey, e);
            return null;
        }
    }

    private String normalizeQuery(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }
        String trimmed = rawQuery.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> parseTags(String cachedValue) {
        String[] tokens = cachedValue.split(",");
        List<String> tags = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String trimmed = token.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty() && !tags.contains(trimmed)) {
                tags.add(trimmed);
            }
        }
        return List.copyOf(tags);
    }

    private List<String> sanitizeTags(String baseQuery, List<String> expanded) {
        List<String> tags = new ArrayList<>();
        tags.add(baseQuery);
        if (expanded != null) {
            for (String tag : expanded) {
                if (tag == null) {
                    continue;
                }
                String normalized = tag.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty() && !tags.contains(normalized)) {
                    tags.add(normalized);
                }
            }
        }
        return List.copyOf(tags);
    }
}
