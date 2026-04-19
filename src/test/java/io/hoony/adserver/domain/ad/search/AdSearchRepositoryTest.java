package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.AdStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [Senior Insight] 통합 테스트의 중요성
 * 유닛 테스트(Unit Test)는 로직을 검증하지만, 통합 테스트(Integration Test)는 
 * 검색 엔진과의 '연결' 및 '매핑 정합성'을 검증합니다.
 * 이 테스트는 우리가 설계한 AdDocument가 실제 Elasticsearch 엔진에서 어떻게 해석되는지 전수 검사합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdSearchRepositoryTest {

    @Autowired
    private AdSearchRepository adSearchRepository;

    @Test
    @DisplayName("Elasticsearch에 광고 도큐먼트를 저장하고 필터링 기반 조회가 정상 작동하는지 확인한다.")
    void saveAndSearchTest() {
        // [1] Given: 테스트 데이터 준비
        AdDocument ad = AdDocument.builder()
                .id(1L)
                .advertiserId(100L)
                .title("테스트 광고")
                .maxBid(new BigDecimal("5000.00"))
                .status(AdStatus.ACTIVE)
                .targetGender("MALE")
                .targetLocationId("1:11")
                .interestTags(List.of("fashion", "shoes")) // 리스트 형태의 태그
                .targetContext(Map.of("age", 25))
                .build();

        // [2] When: ES에 저장 (인덱싱)
        adSearchRepository.save(ad);

        // [3] Then: 검색 엔진의 특성상 인덱싱 후 약간의 딜레이가 있을 수 있으나, 
        // 동기 방식의 save는 즉시 반영을 보장하도록 설정된 경우 바로 조회가 가능합니다.
        List<AdDocument> results = adSearchRepository.findByTargetGenderAndTargetLocationIdAndStatus(
                "MALE", "1:11", "ACTIVE"
        );

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getTitle()).isEqualTo("테스트 광고");
        assertThat(results.get(0).getInterestTags()).containsExactly("fashion", "shoes");
        
        // [Clean up] 다음 테스트를 위해 삭제
        adSearchRepository.delete(ad);
    }
}
