package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;

import java.util.List;

public interface AdSlotMatcher {

    List<AdDocument> match(List<AdDocument> candidates, String slotId);
}
