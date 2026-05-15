package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.AdStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdSearchRepository extends ElasticsearchRepository<AdDocument, Long> {

    List<AdDocument> findByTargetGenderAndTargetLocationIdAndStatus(String gender, String locationId, String status);

    List<AdDocument> findByStatus(AdStatus status);

    List<AdDocument> findByStatus(AdStatus status, Pageable pageable);
}
