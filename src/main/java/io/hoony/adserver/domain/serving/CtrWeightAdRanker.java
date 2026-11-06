package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.adstatistic.AdStatisticDto;
import io.hoony.adserver.domain.adstatistic.AdStatisticService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component("ctr-weight")
public class CtrWeightAdRanker implements AdRanker {

    private static final long DEFAULT_ML_TIMEOUT_MS = 10L;

    private final AdStatisticService adStatisticService;
    private final double alpha;
    private final double beta;
    private final MlPredictor mlPredictor;
    private final long mlTimeoutMs;

    @Autowired
    public CtrWeightAdRanker(
            AdStatisticService adStatisticService,
            @Value("${ad-server.serving.ctr-smoothing-alpha:10.0}") double alpha,
            @Value("${ad-server.serving.ctr-smoothing-beta:1000.0}") double beta
    ) {
        this(adStatisticService, alpha, beta, null, DEFAULT_ML_TIMEOUT_MS);
    }

    public CtrWeightAdRanker(
            AdStatisticService adStatisticService,
            double alpha,
            double beta,
            MlPredictor mlPredictor,
            long mlTimeoutMs
    ) {
        this.adStatisticService = adStatisticService;
        this.alpha = alpha;
        this.beta = beta;
        this.mlPredictor = mlPredictor;
        this.mlTimeoutMs = mlTimeoutMs > 0 ? mlTimeoutMs : DEFAULT_ML_TIMEOUT_MS;
    }

    @Override
    public List<AdDocument> rank(List<AdDocument> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<Long> adIds = extractAdIds(candidates);
        Map<Long, AdStatisticDto> statsMap = adStatisticService != null
                ? adStatisticService.getStatistics(adIds)
                : Map.of();

        // 1단계: 후보 광고별 pCTR 추론(또는 베이지안 Fallback) 및 eCPM 점수 산출
        List<ScoredAd> scoredAds = new ArrayList<>(candidates.size());
        for (AdDocument ad : candidates) {
            double ecpm = calculateScoreWithMap(ad, statsMap);
            scoredAds.add(new ScoredAd(ad, ecpm));
        }

        // 2단계: eCPM 점수가 높은 순서(내림차순)로 정렬
        scoredAds.sort(Comparator.comparingDouble(ScoredAd::ecpm).reversed());

        // 3단계: 정렬된 광고 리스트 추출
        List<AdDocument> rankedAds = new ArrayList<>(scoredAds.size());
        for (ScoredAd scored : scoredAds) {
            rankedAds.add(scored.ad());
        }
        return List.copyOf(rankedAds);
    }

    @Override
    public Optional<AdDocument> select(List<AdDocument> candidates) {
        List<AdDocument> ranked = rank(candidates);
        if (ranked.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ranked.get(0));
    }

    /**
     * [DSP 도메인 공식] eCPM = pCTR * Bid * 1000
     * 1,000회 노출당 기대 매출(effective Cost Per Mille)을 계산한다.
     */
    public double calculateEcpm(double bid, double pCtr) {
        if (bid <= 0.0 || pCtr <= 0.0) {
            return 0.0;
        }
        return pCtr * bid * 1000.0;
    }

    /**
     * ML 모델 서버(gRPC 10ms 타임아웃)에서 pCTR 추론을 시도하고,
     * 미설정·실패·타임아웃 시 즉시 베이지안 평활화 CTR로 Fallback한다.
     */
    public double resolvePredictedCtr(AdDocument ad, AdStatisticDto stat) {
        AdStatisticDto safeStat = stat != null ? stat : new AdStatisticDto(0L, 0L);
        if (mlPredictor != null && ad != null && ad.getId() != null) {
            try {
                String slotId = resolvePrimarySlot(ad);
                OptionalDouble predicted = CompletableFuture
                        .supplyAsync(() -> mlPredictor.predictCtr(ad.getId(), slotId))
                        .get(mlTimeoutMs, TimeUnit.MILLISECONDS);
                if (predicted != null && predicted.isPresent() && predicted.getAsDouble() >= 0.0) {
                    return predicted.getAsDouble();
                }
            } catch (Exception e) {
                log.debug("ML pCTR prediction timed out or failed; falling back to Bayesian CTR. adId={}", ad.getId(), e);
            }
        }
        return safeStat.getSmoothedCtr(alpha, beta);
    }

    private double calculateScoreWithMap(AdDocument ad, Map<Long, AdStatisticDto> statsMap) {
        double bid = ad.getMaxBid() == null ? 0.0 : ad.getMaxBid().doubleValue();
        AdStatisticDto stat = statsMap.getOrDefault(ad.getId(), new AdStatisticDto(0L, 0L));
        double pCtr = resolvePredictedCtr(ad, stat);
        double ecpm = calculateEcpm(bid, pCtr);
        log.debug("Ad ranker calculated eCPM: adId={}, bid={}, impressions={}, clicks={}, pCtr={}, ecpm={}",
                ad.getId(), bid, stat.impressions(), stat.clicks(), pCtr, ecpm);
        return ecpm;
    }

    private List<Long> extractAdIds(List<AdDocument> candidates) {
        List<Long> adIds = new ArrayList<>(candidates.size());
        for (AdDocument ad : candidates) {
            adIds.add(ad.getId());
        }
        return List.copyOf(adIds);
    }

    private String resolvePrimarySlot(AdDocument ad) {
        List<String> slotIds = ad.getSlotIds();
        if (slotIds == null || slotIds.isEmpty()) {
            return AdDocument.ALL_SLOTS;
        }
        return slotIds.get(0);
    }

    private record ScoredAd(AdDocument ad, double ecpm) {
    }
}
