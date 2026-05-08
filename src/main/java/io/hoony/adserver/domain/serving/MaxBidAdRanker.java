package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class MaxBidAdRanker implements AdRanker {

    @Override
    public Optional<AdDocument> select(List<AdDocument> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .max(Comparator.comparing(ad -> ad.getMaxBid() == null ? BigDecimal.ZERO : ad.getMaxBid()));
    }
}
