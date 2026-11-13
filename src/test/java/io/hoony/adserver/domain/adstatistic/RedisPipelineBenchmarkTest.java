package io.hoony.adserver.domain.adstatistic;

import io.hoony.adserver.config.TracingSupport;
import io.hoony.adserver.domain.ad.AdRepository;
import io.hoony.adserver.domain.adevent.AdEventRepository;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step 21: multiGet/multiSet 배치와 Redis Pipelining(executePipelined) 간
 * 네트워크 왕복(RTT) 횟수 및 처리량 비교 마이크로 테스트.
 */
class RedisPipelineBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(RedisPipelineBenchmarkTest.class);
    private static final long SIMULATED_RTT_NANOS = 1_000_000L; // 1ms local/VPC RTT model

    static boolean isLiveRedisBenchmarkEnabled() {
        return "true".equalsIgnoreCase(System.getProperty("redis.benchmark.enabled"))
                || "true".equalsIgnoreCase(System.getenv("REDIS_BENCHMARK"));
    }

    @Test
    @DisplayName("대량 200건 캐시 미스 시 AdStatisticService.getStatistics는 1회 multiGet + 1회 multiSet(총 2 RTT)만 수행한다")
    @SuppressWarnings("unchecked")
    void shouldUseSingleBatchWriteForLargeCandidateMisses() {
        int candidateCount = 200; // L1 ES 후보 상한과 동일
        List<Long> adIds = new ArrayList<>(candidateCount);
        List<AdStatistic> dbStats = new ArrayList<>(candidateCount);
        for (long id = 1; id <= candidateCount; id++) {
            adIds.add(id);
            dbStats.add(AdStatistic.of(id, id * 100, id * 2));
        }

        AdStatisticRepository adStatisticRepository = mock(AdStatisticRepository.class);
        AdRepository adRepository = mock(AdRepository.class);
        AdEventRepository adEventRepository = mock(AdEventRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(adStatisticRepository.findAllById(any())).thenReturn(dbStats);

        AtomicInteger multiGetCalls = new AtomicInteger();
        AtomicInteger multiSetCalls = new AtomicInteger();
        AtomicInteger singleSetCalls = new AtomicInteger();
        Map<String, String> writtenEntries = new LinkedHashMap<>();

        when(valueOperations.multiGet(anyList())).thenAnswer(invocation -> {
            multiGetCalls.incrementAndGet();
            List<String> keys = invocation.getArgument(0);
            return new ArrayList<>(Collections.nCopies(keys.size(), null));
        });
        doAnswer(invocation -> {
            multiSetCalls.incrementAndGet();
            writtenEntries.putAll(invocation.getArgument(0));
            return null;
        }).when(valueOperations).multiSet(anyMap());
        doAnswer(invocation -> {
            singleSetCalls.incrementAndGet();
            return null;
        }).when(valueOperations).set(anyString(), anyString());

        AdStatisticService service = new AdStatisticService(
                adStatisticRepository,
                adRepository,
                redisTemplate,
                adEventRepository,
                new TracingSupport(ObservationRegistry.NOOP)
        );

        Map<Long, AdStatisticDto> result = service.getStatistics(adIds);

        assertThat(result).hasSize(candidateCount);
        assertThat(multiGetCalls.get()).isEqualTo(1);
        assertThat(multiSetCalls.get()).isEqualTo(1);
        assertThat(singleSetCalls.get()).isZero();
        assertThat(writtenEntries).hasSize(candidateCount * 2);
        assertThat(writtenEntries).containsEntry("ad:stat:imp:200", "20000");
        assertThat(writtenEntries).containsEntry("ad:stat:clk:200", "400");
    }

    @Test
    @DisplayName("모의 RTT(1ms) 환경에서 단건 set 루프(2N RTT) 대비 multiSet(1 RTT) 및 executePipelined(1 RTT)의 왕복 지연을 비교한다")
    @SuppressWarnings("unchecked")
    void shouldCompareSimulatedRttAcrossSingleSetMultiSetAndPipelining() {
        int adCount = 50; // 100 keys
        Map<String, String> entries = new LinkedHashMap<>(adCount * 2);
        for (long id = 1; id <= adCount; id++) {
            entries.put("ad:stat:imp:" + id, String.valueOf(id * 100));
            entries.put("ad:stat:clk:" + id, String.valueOf(id * 2));
        }

        Map<String, String> store = new ConcurrentHashMap<>();
        AtomicInteger singleSetRtt = new AtomicInteger();
        AtomicInteger multiSetRtt = new AtomicInteger();
        AtomicInteger pipelineRtt = new AtomicInteger();
        AtomicInteger pipelinedCommandCount = new AtomicInteger();

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        doAnswer(invocation -> {
            LockSupport.parkNanos(SIMULATED_RTT_NANOS);
            singleSetRtt.incrementAndGet();
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString());

        doAnswer(invocation -> {
            LockSupport.parkNanos(SIMULATED_RTT_NANOS);
            multiSetRtt.incrementAndGet();
            store.putAll(invocation.getArgument(0));
            return null;
        }).when(valueOperations).multiSet(anyMap());

        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands stringCommands = mock(RedisStringCommands.class);
        when(connection.stringCommands()).thenReturn(stringCommands);
        when(stringCommands.set(any(byte[].class), any(byte[].class))).thenAnswer(invocation -> {
            pipelinedCommandCount.incrementAndGet();
            byte[] key = invocation.getArgument(0);
            byte[] val = invocation.getArgument(1);
            store.put(new String(key, StandardCharsets.UTF_8), new String(val, StandardCharsets.UTF_8));
            return Boolean.TRUE;
        });

        when(redisTemplate.executePipelined(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            callback.doInRedis(connection);
            LockSupport.parkNanos(SIMULATED_RTT_NANOS); // single pipeline flush RTT
            pipelineRtt.incrementAndGet();
            return List.of();
        });

        long singleStart = System.nanoTime();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            redisTemplate.opsForValue().set(entry.getKey(), entry.getValue());
        }
        long singleElapsedNs = System.nanoTime() - singleStart;

        store.clear();
        long multiSetStart = System.nanoTime();
        redisTemplate.opsForValue().multiSet(entries);
        long multiSetElapsedNs = System.nanoTime() - multiSetStart;

        store.clear();
        long pipelineStart = System.nanoTime();
        redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                conn.stringCommands().set(
                        entry.getKey().getBytes(StandardCharsets.UTF_8),
                        entry.getValue().getBytes(StandardCharsets.UTF_8)
                );
            }
            return null;
        });
        long pipelineElapsedNs = System.nanoTime() - pipelineStart;

        assertThat(singleSetRtt.get()).isEqualTo(adCount * 2);
        assertThat(multiSetRtt.get()).isEqualTo(1);
        assertThat(pipelineRtt.get()).isEqualTo(1);
        assertThat(pipelinedCommandCount.get()).isEqualTo(adCount * 2);
        assertThat(store).hasSize(adCount * 2);

        assertThat(multiSetElapsedNs).isLessThan(singleElapsedNs / 5);
        assertThat(pipelineElapsedNs).isLessThan(singleElapsedNs / 5);

        log.info(
                "[Simulated 1ms RTT, keys={}] singleSet={}ms ({} RTT), multiSet={}ms ({} RTT), executePipelined={}ms ({} RTT)",
                entries.size(),
                String.format("%.2f", singleElapsedNs / 1_000_000.0), singleSetRtt.get(),
                String.format("%.2f", multiSetElapsedNs / 1_000_000.0), multiSetRtt.get(),
                String.format("%.2f", pipelineElapsedNs / 1_000_000.0), pipelineRtt.get()
        );
    }

    @Test
    @EnabledIf("isLiveRedisBenchmarkEnabled")
    @DisplayName("[Live Redis] 대량 adIds(200건 / 1000건) 기준 단건 루프 vs multiGet/multiSet vs executePipelined 처리량 비교")
    void benchmarkLiveRedisBatchVsPipelining() {
        String host = System.getProperty("redis.host", "localhost");
        int port = Integer.parseInt(System.getProperty("redis.port", "6379"));

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        String prefix = "bench:step21:" + UUID.randomUUID() + ":";
        List<String> createdKeys = new ArrayList<>();

        try {
            // Warmup connection & serializer
            String warmupKey = prefix + "warmup";
            createdKeys.add(warmupKey);
            redisTemplate.opsForValue().set(warmupKey, "1");
            assertThat(redisTemplate.opsForValue().get(warmupKey)).isEqualTo("1");

            for (int adCount : List.of(200, 1000)) {
                int iterations = adCount <= 200 ? 20 : 5;
                List<String> keys = new ArrayList<>(adCount * 2);
                Map<String, String> payload = new LinkedHashMap<>(adCount * 2);
                for (int i = 1; i <= adCount; i++) {
                    String impKey = prefix + adCount + ":imp:" + i;
                    String clkKey = prefix + adCount + ":clk:" + i;
                    keys.add(impKey);
                    keys.add(clkKey);
                    payload.put(impKey, String.valueOf(i * 100L));
                    payload.put(clkKey, String.valueOf(i * 2L));
                }
                createdKeys.addAll(keys);

                long singleWriteNs = measureAverageNanos(iterations, () -> {
                    for (Map.Entry<String, String> entry : payload.entrySet()) {
                        redisTemplate.opsForValue().set(entry.getKey(), entry.getValue());
                    }
                });

                long multiSetNs = measureAverageNanos(iterations, () ->
                        redisTemplate.opsForValue().multiSet(payload));

                long pipelineWriteNs = measureAverageNanos(iterations, () ->
                        redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
                            for (Map.Entry<String, String> entry : payload.entrySet()) {
                                conn.stringCommands().set(
                                        entry.getKey().getBytes(StandardCharsets.UTF_8),
                                        entry.getValue().getBytes(StandardCharsets.UTF_8)
                                );
                            }
                            return null;
                        }));

                long singleReadNs = measureAverageNanos(iterations, () -> {
                    for (String key : keys) {
                        redisTemplate.opsForValue().get(key);
                    }
                });

                long multiGetNs = measureAverageNanos(iterations, () -> {
                    List<String> values = redisTemplate.opsForValue().multiGet(keys);
                    assertThat(values).hasSize(keys.size());
                });

                long pipelineReadNs = measureAverageNanos(iterations, () -> {
                    List<Object> values = redisTemplate.executePipelined((RedisCallback<Object>) conn -> {
                        for (String key : keys) {
                            conn.stringCommands().get(key.getBytes(StandardCharsets.UTF_8));
                        }
                        return null;
                    });
                    assertThat(values).hasSize(keys.size());
                });

                log.info(
                        "[Live Redis adCount={}, keys={}, avg of {} runs] WRITE singleSet={}ms ({} ops/s), multiSet={}ms ({} ops/s), pipelineSet={}ms ({} ops/s)",
                        adCount, keys.size(), iterations,
                        formatMs(singleWriteNs), formatOpsPerSec(keys.size(), singleWriteNs),
                        formatMs(multiSetNs), formatOpsPerSec(keys.size(), multiSetNs),
                        formatMs(pipelineWriteNs), formatOpsPerSec(keys.size(), pipelineWriteNs)
                );
                log.info(
                        "[Live Redis adCount={}, keys={}, avg of {} runs] READ  singleGet={}ms ({} ops/s), multiGet={}ms ({} ops/s), pipelineGet={}ms ({} ops/s)",
                        adCount, keys.size(), iterations,
                        formatMs(singleReadNs), formatOpsPerSec(keys.size(), singleReadNs),
                        formatMs(multiGetNs), formatOpsPerSec(keys.size(), multiGetNs),
                        formatMs(pipelineReadNs), formatOpsPerSec(keys.size(), pipelineReadNs)
                );
            }
        } finally {
            if (!createdKeys.isEmpty()) {
                redisTemplate.delete(createdKeys);
            }
            connectionFactory.destroy();
        }
    }

    private long measureAverageNanos(int iterations, Runnable action) {
        long total = 0L;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            action.run();
            total += System.nanoTime() - start;
        }
        return total / iterations;
    }

    private String formatMs(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }

    private long formatOpsPerSec(int keyCount, long nanos) {
        if (nanos <= 0) {
            return 0L;
        }
        return Math.round(keyCount / (nanos / 1_000_000_000.0));
    }
}
