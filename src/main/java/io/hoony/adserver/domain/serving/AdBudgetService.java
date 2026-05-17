package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import java.util.List;

public interface AdBudgetService {

    boolean trySpend(AdDocument ad);

    boolean isExhausted(AdDocument ad);

    default List<AdDocument> filterExhausted(List<AdDocument> ads) {
        if (ads == null || ads.isEmpty()) {
            return List.of();
        }
        return ads.stream().filter(ad -> !isExhausted(ad)).toList();
    }
}
