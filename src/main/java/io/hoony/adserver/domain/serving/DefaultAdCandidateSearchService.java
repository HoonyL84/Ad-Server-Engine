package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.ad.search.AdSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultAdCandidateSearchService implements AdCandidateSearchService {

    private static final int MAX_CANDIDATES = 200;

    private final AdSearchRepository adSearchRepository;

    @Override
    public List<AdDocument> searchCandidates(String slotId) {
        List<AdDocument> candidates = adSearchRepository.findByStatus(AdStatus.ACTIVE);
        return candidates.stream()
                .sorted(Comparator.comparing(AdDocument::getMaxBid).reversed())
                .limit(MAX_CANDIDATES)
                .toList();
    }
}

