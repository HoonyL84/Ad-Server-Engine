package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.ad.search.AdSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultAdCandidateSearchService implements AdCandidateSearchService {

    private static final int MAX_CANDIDATES = 200;

    private final AdSearchRepository adSearchRepository;

    @Override
    public List<AdDocument> searchCandidates(String slotId) {
        PageRequest pageRequest = PageRequest.of(
                0,
                MAX_CANDIDATES,
                Sort.by(Sort.Direction.DESC, "maxBid")
        );
        return adSearchRepository.findByStatus(AdStatus.ACTIVE, pageRequest);
    }
}
