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

@SpringBootTest
@ActiveProfiles("test")
class AdSearchRepositoryTest {

    @Autowired
    private AdSearchRepository adSearchRepository;

    @Test
    @DisplayName("Elasticsearch에 광고 도큐먼트를 저장하고 필터링 기반 조회가 정상 작동하는지 확인한다.")
    void saveAndSearchTest() {
        AdDocument ad = AdDocument.builder()
                .id(1L)
                .advertiserId(100L)
                .title("테스트 광고")
                .maxBid(new BigDecimal("5000.00"))
                .status(AdStatus.ACTIVE)
                .targetGender("MALE")
                .targetLocationId("1:11")
                .interestTags(List.of("fashion", "shoes"))
                .targetContext(Map.of("age", 25))
                .build();

        adSearchRepository.save(ad);

        List<AdDocument> results = adSearchRepository.findByTargetGenderAndTargetLocationIdAndStatus(
                "MALE", "1:11", "ACTIVE"
        );

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getTitle()).isEqualTo("테스트 광고");
        assertThat(results.get(0).getInterestTags()).containsExactly("fashion", "shoes");
        
        adSearchRepository.delete(ad);
    }
}
