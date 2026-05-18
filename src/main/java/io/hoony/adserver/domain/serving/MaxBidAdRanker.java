package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component("max-bid")
public class MaxBidAdRanker implements AdRanker {

    @Override
    public List<AdDocument> rank(List<AdDocument> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .sorted(Comparator.comparing(this::bidOrZero).reversed())
                .toList();
    }

    @Override
    public Optional<AdDocument> select(List<AdDocument> candidates) {
        return rank(candidates).stream().findFirst();
    }

    private BigDecimal bidOrZero(AdDocument ad) {
        return ad.getMaxBid() == null ? BigDecimal.ZERO : ad.getMaxBid();
    }
}
