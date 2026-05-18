package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.adstatistic.AdStatisticDto;
import io.hoony.adserver.domain.adstatistic.AdStatisticService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component("ctr-weight")
public class CtrWeightAdRanker implements AdRanker {

    private final AdStatisticService adStatisticService;
    private final double alpha;
    private final double beta;

    public CtrWeightAdRanker(
            AdStatisticService adStatisticService,
            @Value("${ad-server.serving.ctr-smoothing-alpha:10.0}") double alpha,
            @Value("${ad-server.serving.ctr-smoothing-beta:1000.0}") double beta
    ) {
        this.adStatisticService = adStatisticService;
        this.alpha = alpha;
        this.beta = beta;
    }

    @Override
    public List<AdDocument> rank(List<AdDocument> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble(this::calculateScore).reversed())
                .toList();
    }

    @Override
    public Optional<AdDocument> select(List<AdDocument> candidates) {
        return rank(candidates).stream().findFirst();
    }

    private double calculateScore(AdDocument ad) {
        double bid = ad.getMaxBid() == null ? 0.0 : ad.getMaxBid().doubleValue();
        AdStatisticDto stat = adStatisticService.getStatistic(ad.getId());
        double ctr = stat.getSmoothedCtr(alpha, beta);
        double score = bid * ctr;
        log.debug("Ad ranker calculated score: adId={}, bid={}, impressions={}, clicks={}, smoothedCtr={}, score={}",
                ad.getId(), bid, stat.impressions(), stat.clicks(), ctr, score);
        return score;
    }
}
