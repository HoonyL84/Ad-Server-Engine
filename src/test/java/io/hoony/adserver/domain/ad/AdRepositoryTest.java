package io.hoony.adserver.domain.ad;

import io.hoony.adserver.config.JpaConfig;
import io.hoony.adserver.domain.advertiser.Advertiser;
import io.hoony.adserver.domain.advertiser.AdvertiserRepository;
import io.hoony.adserver.domain.advertiser.AdvertiserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.math.BigDecimal;
import java.util.Map;

import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // 실제 로컬 DB(3306) 사용
class AdRepositoryTest {

    @Autowired
    private AdRepository adRepository;

    @Autowired
    private AdvertiserRepository advertiserRepository;

    @Test
    @DisplayName("광고주와 광고를 저장하고 JSON 형태의 타겟팅 컨텍스트가 정상적으로 저장되는지 확인한다.")
    void saveAndFindAd() {
        // given
        Advertiser advertiser = Advertiser.builder()
                .name("삼성전자")
                .status(AdvertiserStatus.ACTIVE)
                .build();
        advertiserRepository.save(advertiser);

        Map<String, Object> targetContext = Map.of(
                "region", "SEOUL",
                "gender", "MALE",
                "age_range", Map.of("min", 20, "max", 35)
        );

        Ad ad = Ad.builder()
                .title("삼성전자 갤럭시 S24 광고")
                .imageUrl("https://image.samsung.com/galaxy-s24.jpg")
                .clickUrl("https://www.samsung.com/galaxy-s24")
                .advertiser(advertiser)
                .maxBid(new BigDecimal("100.00"))
                .totalBudget(new BigDecimal("1000000.00"))
                .startDate(java.time.LocalDateTime.now())
                .targetGender("MALE")
                .targetLocationId("1:11")
                .targetInterestTags("ELECTRONICS")
                .targetContext(targetContext)
                .build();

        // when
        Ad savedAd = adRepository.save(ad);
        adRepository.flush();

        // then
        Ad foundAd = adRepository.findByIdWithAdvertiser(savedAd.getId());
        assertThat(foundAd).isNotNull();
        assertThat(foundAd.getAdvertiser().getName()).isEqualTo("삼성전자");
        assertThat(foundAd.getMaxBid().compareTo(new BigDecimal("100.00"))).isZero();
        assertThat(foundAd.getTargetContext().get("region")).isEqualTo("SEOUL");
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> ageRange = (Map<String, Integer>) foundAd.getTargetContext().get("age_range");
        assertThat(ageRange.get("min")).isEqualTo(20);
    }

    @Test
    @DisplayName("대량의 광고 데이터를 일괄 저장하고 전체 개수를 확인한다.")
    void saveMultipleAdsAndRetrieveThem() {
        // given: 10명의 광고주에게 각각 10개씩, 총 100개의 광고를 생성한다.
        for (int i = 1; i <= 10; i++) {
            Advertiser advertiser = new Advertiser("광고주_" + i, AdvertiserStatus.ACTIVE);
            advertiserRepository.save(advertiser);

            for (int k = 1; k <= 10; k++) {
                Ad ad = Ad.builder()
                        .title("대량 테스트 광고_" + i + "_" + k)
                        .imageUrl("https://example.com/test-ad-" + (i * k) + ".png")
                        .clickUrl("https://example.com/landing-" + (i * k))
                        .advertiser(advertiser)
                        .totalBudget(new BigDecimal("1000000"))
                        .maxBid(new BigDecimal("5000"))
                        .startDate(java.time.LocalDateTime.now())
                        .targetGender("ALL")
                        .targetLocationId("1:11")
                        .targetInterestTags("FURNITURE")
                        .targetContext(Map.of("category", "FURNITURE", "tag", "BED"))
                        .build();
                adRepository.save(ad);
            }
        }

        // when: 전체 광고 개수를 조회한다.
        long totalCount = adRepository.count();

        // then: 정확히 100개가 저장되었는지 확인한다.
        assertThat(totalCount).isEqualTo(100L);
    }
}
