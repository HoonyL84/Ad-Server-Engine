package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;

import java.util.List;

public interface AdCandidateSearchService {
    List<AdDocument> searchCandidates(String slotId);
}

