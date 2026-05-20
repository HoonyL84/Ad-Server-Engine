package io.hoony.adserver.domain.adstatistic;

import io.hoony.adserver.domain.ad.AdRepository;
import io.hoony.adserver.domain.adevent.AdEventRepository;
import io.hoony.adserver.domain.adevent.AdEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdStatisticServiceTest {

    private AdStatisticRepository adStatisticRepository;
    private AdRepository adRepository;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private AdEventRepository adEventRepository;
    private AdStatisticService adStatisticService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        adStatisticRepository = mock(AdStatisticRepository.class);
        adRepository = mock(AdRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        adEventRepository = mock(AdEventRepository.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        adStatisticService = new AdStatisticService(adStatisticRepository, adRepository, redisTemplate, adEventRepository);
    }

    @Test
    @DisplayName("Redis 캐시가 존재할 때 Redis에서 노출/클릭 지표를 읽고 DTO를 반환한다")
    void shouldReturnDtoFromRedisCache() {
        Long adId = 1L;
        when(valueOperations.get("ad:stat:imp:1")).thenReturn("5000");
        when(valueOperations.get("ad:stat:clk:1")).thenReturn("150");

        AdStatisticDto dto = adStatisticService.getStatistic(adId);

        assertThat(dto.impressions()).isEqualTo(5000L);
        assertThat(dto.clicks()).isEqualTo(150L);
        verifyNoInteractions(adStatisticRepository);
    }

    @Test
    @DisplayName("Redis 캐시가 없을 때 DB에서 통계를 읽은 후 Redis에 캐싱하고 DTO를 반환한다")
    void shouldFallbackToDatabaseWhenCacheMiss() {
        Long adId = 1L;
        when(valueOperations.get("ad:stat:imp:1")).thenReturn(null);
        when(valueOperations.get("ad:stat:clk:1")).thenReturn(null);

        AdStatistic dbStat = AdStatistic.of(adId, 3000L, 80L);
        when(adStatisticRepository.findById(adId)).thenReturn(Optional.of(dbStat));

        AdStatisticDto dto = adStatisticService.getStatistic(adId);

        assertThat(dto.impressions()).isEqualTo(3000L);
        assertThat(dto.clicks()).isEqualTo(80L);

        verify(valueOperations).set("ad:stat:imp:1", "3000");
        verify(valueOperations).set("ad:stat:clk:1", "80");
    }

    @Test
    @DisplayName("Redis에 일부 카운터만 있어도 기존 Redis 값을 유지하고 없는 값만 DB에서 보완한다")
    void shouldPreservePartialRedisCounter() {
        Long adId = 1L;
        when(valueOperations.get("ad:stat:imp:1")).thenReturn("10");
        when(valueOperations.get("ad:stat:clk:1")).thenReturn(null);

        AdStatistic dbStat = AdStatistic.of(adId, 3000L, 80L);
        when(adStatisticRepository.findById(adId)).thenReturn(Optional.of(dbStat));

        AdStatisticDto dto = adStatisticService.getStatistic(adId);

        assertThat(dto.impressions()).isEqualTo(10L);
        assertThat(dto.clicks()).isEqualTo(80L);

        verify(valueOperations, never()).set("ad:stat:imp:1", "3000");
        verify(valueOperations).set("ad:stat:clk:1", "80");
    }

    @Test
    @DisplayName("벌크 조회 시 Redis에 일부 카운터만 존재하더라도, 기존 값을 유지한 채 누락된 값만 DB에서 로드 및 캐싱한다")
    void shouldPreservePartialRedisCounterInBulk() {
        List<Long> adIds = List.of(1L, 2L);
        // multiGet 반환: 1L(imp=10, clk=null), 2L(imp=null, clk=30)
        when(valueOperations.multiGet(anyList())).thenReturn(java.util.Arrays.asList("10", null, null, "30"));

        when(adStatisticRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(
                AdStatistic.of(1L, 5000L, 150L),
                AdStatistic.of(2L, 8000L, 200L)
        ));

        java.util.Map<Long, AdStatisticDto> stats = adStatisticService.getStatistics(adIds);

        assertThat(stats.get(1L).impressions()).isEqualTo(10L); // Redis 기존 값 보존
        assertThat(stats.get(1L).clicks()).isEqualTo(150L);      // DB에서 보완됨
        assertThat(stats.get(2L).impressions()).isEqualTo(8000L); // DB에서 보완됨
        assertThat(stats.get(2L).clicks()).isEqualTo(30L);       // Redis 기존 값 보존

        // Redis 누락된 대상만 set이 가야 함
        verify(valueOperations, never()).set("ad:stat:imp:1", "10");
        verify(valueOperations, never()).set("ad:stat:imp:1", "5000");
        verify(valueOperations).set("ad:stat:clk:1", "150");

        verify(valueOperations).set("ad:stat:imp:2", "8000");
        verify(valueOperations, never()).set("ad:stat:clk:2", "30");
        verify(valueOperations, never()).set("ad:stat:clk:2", "200");
    }

    @Test
    @DisplayName("주기적 동기화 시 Redis의 통계 수치를 DB에 영속화한다")
    @SuppressWarnings("unchecked")
    void shouldSyncStatisticsToDatabase() {
        List<Long> adIds = List.of(1L, 2L);
        when(adRepository.findAllIds()).thenReturn(adIds);
        when(adStatisticRepository.findAllById(adIds)).thenReturn(List.of(
                AdStatistic.of(1L, 9000L, 200L),
                AdStatistic.of(2L, 18000L, 500L)
        ));

        when(valueOperations.multiGet(List.of(
                "ad:stat:imp:1",
                "ad:stat:clk:1",
                "ad:stat:imp:2",
                "ad:stat:clk:2"
        ))).thenReturn(List.of("10000", "250", "20000", "600"));

        adStatisticService.syncToDatabase();

        ArgumentCaptor<List<AdStatistic>> captor = ArgumentCaptor.forClass(List.class);
        verify(adStatisticRepository).saveAll(captor.capture());

        List<AdStatistic> stats = captor.getValue();
        assertThat(stats).hasSize(2);

        AdStatistic stat1 = stats.stream().filter(s -> s.getAdId().equals(1L)).findFirst().orElseThrow();
        assertThat(stat1.getImpressionCount()).isEqualTo(10000L);
        assertThat(stat1.getClickCount()).isEqualTo(250L);

        AdStatistic stat2 = stats.stream().filter(s -> s.getAdId().equals(2L)).findFirst().orElseThrow();
        assertThat(stat2.getImpressionCount()).isEqualTo(20000L);
        assertThat(stat2.getClickCount()).isEqualTo(600L);
    }

    @Test
    @DisplayName("주기적 동기화 시 Redis에 없는 카운터는 기존 DB 값을 유지한다")
    @SuppressWarnings("unchecked")
    void shouldPreserveDatabaseCounterWhenRedisCounterIsMissing() {
        List<Long> adIds = List.of(1L);
        when(adRepository.findAllIds()).thenReturn(adIds);
        when(adStatisticRepository.findAllById(adIds)).thenReturn(List.of(
                AdStatistic.of(1L, 9000L, 200L)
        ));

        when(valueOperations.multiGet(List.of(
                "ad:stat:imp:1",
                "ad:stat:clk:1"
        ))).thenReturn(java.util.Arrays.asList("10000", null));

        adStatisticService.syncToDatabase();

        ArgumentCaptor<List<AdStatistic>> captor = ArgumentCaptor.forClass(List.class);
        verify(adStatisticRepository).saveAll(captor.capture());

        AdStatistic stat = captor.getValue().get(0);
        assertThat(stat.getImpressionCount()).isEqualTo(10000L);
        assertThat(stat.getClickCount()).isEqualTo(200L);
    }

    @Test
    @DisplayName("이벤트 원장 보정 배치는 GROUP BY 집계 결과로 Redis와 DB 통계를 다시 맞춘다")
    @SuppressWarnings("unchecked")
    void shouldRealignStatisticsFromGroupedEventLedger() {
        List<Long> adIds = List.of(1L, 2L);
        when(adRepository.findAllIds()).thenReturn(adIds);
        when(adEventRepository.countAllEventsGrouped()).thenReturn(List.of(
                new AdEventCountDto(1L, AdEventType.IMPRESSION, 10L),
                new AdEventCountDto(1L, AdEventType.CLICK, 2L),
                new AdEventCountDto(2L, AdEventType.IMPRESSION, 5L)
        ));

        adStatisticService.realignStatisticsFromEventLedger();

        verify(valueOperations).multiSet(java.util.Map.of(
                "ad:stat:imp:1", "10",
                "ad:stat:clk:1", "2",
                "ad:stat:imp:2", "5",
                "ad:stat:clk:2", "0"
        ));

        ArgumentCaptor<List<AdStatistic>> captor = ArgumentCaptor.forClass(List.class);
        verify(adStatisticRepository).saveAll(captor.capture());

        List<AdStatistic> stats = captor.getValue();
        assertThat(stats).hasSize(2);

        AdStatistic stat1 = stats.stream().filter(s -> s.getAdId().equals(1L)).findFirst().orElseThrow();
        assertThat(stat1.getImpressionCount()).isEqualTo(10L);
        assertThat(stat1.getClickCount()).isEqualTo(2L);

        AdStatistic stat2 = stats.stream().filter(s -> s.getAdId().equals(2L)).findFirst().orElseThrow();
        assertThat(stat2.getImpressionCount()).isEqualTo(5L);
        assertThat(stat2.getClickCount()).isZero();

        verify(adEventRepository).countAllEventsGrouped();
        verify(adEventRepository, never()).countByAdIdAndEventType(any(), any());
    }
}
