package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;

public interface AdBudgetService {

    boolean trySpend(AdDocument ad);
}
