package io.hoony.adserver.domain.ad.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import io.hoony.adserver.domain.ad.AdStatus;
import java.util.List;

/**
 * [Senior Insight] Repository 패턴의 통일성
 * Spring Data JPA와 동일한 Repository 패턴을 Elasticsearch에도 적용합니다.
 * 이 구조는 서비스 계층에서 저장소의 물리적 위치(MySQL vs ES)에 관계없이 
 * 일관된 인터페이스로 데이터를 조회할 수 있게 해줍니다.
 */
@Repository
public interface AdSearchRepository extends ElasticsearchRepository<AdDocument, Long> {

    /**
     * 필터링 쿼리 예시: 특정 성별, 특정 지역을 동시에 만족하는 광고 후보군 조회
     * 실제 서빙 시에는 Query DSL을 사용하여 더 복잡한 '조합 쿼리'를 구현할 예정입니다.
     */
    List<AdDocument> findByTargetGenderAndTargetLocationIdAndStatus(String gender, String locationId, String status);

    List<AdDocument> findByStatus(AdStatus status);

}
