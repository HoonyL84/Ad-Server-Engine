package io.hoony.adserver.domain.ad;

import io.hoony.adserver.domain.advertiser.Advertiser;
import io.hoony.adserver.domain.advertiser.AdvertiserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [Master Insight] 현실적인 비즈니스 데이터 시뮬레이션
 * 단순히 데이터를 넣는 행위를 넘어, 버티컬 플랫폼(패션, 로컬, 홈)의 
 * 지면 특성과 사용자 페르소나를 데이터 분포(Bias)로 재현합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdDataSeeder implements CommandLineRunner {

    private final AdRepository adRepository;
    private final AdvertiserRepository advertiserRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static final int TOTAL_ADS = 300;

    private enum PersonaType {
        FASHION, LOCAL, HOME, GENERAL
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("마스터급 고성능 벌크 시딩(목표: {}건)을 시작합니다...", TOTAL_ADS);
        
        // 1. 멱등성 보장을 위한 초기화 (JDBC 기반)
        jdbcTemplate.update("DELETE FROM ad");
        
        List<Advertiser> advertisers = advertiserRepository.findAll();
        if (advertisers.isEmpty()) {
            log.warn("광고주 데이터 누락. 시딩 중단.");
            return;
        }

        // 2. 타입 안전한 페르소나 매핑 (운영 안정성 확보)
        Map<String, PersonaType> advertiserPersonaMap = Map.of(
            "패션 브랜드", PersonaType.FASHION,
            "로컬 상점", PersonaType.LOCAL,
            "홈 스타일러스", PersonaType.HOME
        );

        Map<PersonaType, List<String>> personaTags = Map.of(
            PersonaType.FASHION, Arrays.asList("패션", "뷰티", "신발", "운동", "액세서리"),
            PersonaType.LOCAL, Arrays.asList("로컬", "중고", "알바", "맛집", "반려동물"),
            PersonaType.HOME, Arrays.asList("가구", "인테리어", "리빙", "조명", "캠핑")
        );

        List<String> generalTags = Arrays.asList("금융", "게임", "자동차", "건강", "육아", "영화", "독서", "음악", "코인", "재테크");
        String sql = "INSERT INTO ad (advertiser_id, title, image_url, click_url, max_bid, total_budget, spent_amount, start_date, status, target_gender, target_location_id, target_interest_tags, target_context, created_at, modified_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        List<Object[]> batchArgs = new ArrayList<>();
        int batchSize = 1000;
        ThreadLocalRandom random = ThreadLocalRandom.current(); 
        LocalDateTime now = LocalDateTime.now();

        for (int i = 1; i <= TOTAL_ADS; i++) {
            Advertiser advertiser = advertisers.get(random.nextInt(advertisers.size()));
            PersonaType persona = advertiserPersonaMap.getOrDefault(advertiser.getName(), PersonaType.GENERAL);
            
            // 3. 의도된 입찰가 분포 (Ranking Test용 포인트)
            BigDecimal maxBid;
            int distributionRand = random.nextInt(100);
            if (distributionRand < 10) { 
                maxBid = BigDecimal.valueOf(random.nextInt(1001) + 4000); // 상위 10%: High Bid
            } else if (distributionRand < 70) { 
                maxBid = BigDecimal.valueOf(random.nextInt(2001) + 1000); // 중간 60%: Normal
            } else { 
                maxBid = BigDecimal.valueOf(random.nextInt(401) + 100);   // 하위 30%: Low Bid
            }

            // 4. 관심사 태그 (Interest)
            List<String> tags = new ArrayList<>();
            List<String> preferredTags = personaTags.getOrDefault(persona, generalTags);
            int tagCount = random.nextInt(3) + 1;
            for (int j = 0; j < tagCount; j++) {
                tags.add(random.nextInt(100) < 85 ? preferredTags.get(random.nextInt(preferredTags.size())) : generalTags.get(random.nextInt(generalTags.size())));
            }
            String finalTags = String.join(",", new HashSet<>(tags));

            // 5. 타입별 타겟팅 바이어스 (Gender, Region)
            String gender = "ALL";
            String location = "0";

            if (persona == PersonaType.FASHION) {
                gender = random.nextBoolean() ? "F" : "M";
            } else if (persona == PersonaType.LOCAL) {
                // 특정 지역(1:11) 밀집도 60% 시뮬레이션
                location = random.nextInt(100) < 60 ? "1:11" : Arrays.asList("1:12", "1:13", "1:14", "2:21", "2:22", "3:31", "4:41", "5:51", "9:99", "0").get(random.nextInt(10));
            } else {
                gender = Arrays.asList("M", "F", "ALL").get(random.nextInt(3));
                location = Arrays.asList("1:11", "1:12", "1:13", "1:14", "2:21", "2:22", "3:31", "4:41", "5:51", "9:99", "0").get(random.nextInt(11));
            }

            // 6. 예산 시나리오
            BigDecimal totalBudget = BigDecimal.valueOf((random.nextInt(100) + 1) * 100000);
            BigDecimal spentAmount = random.nextInt(100) < 5 ? totalBudget.subtract(BigDecimal.valueOf(random.nextInt(100))) : totalBudget.multiply(BigDecimal.valueOf(random.nextDouble() * 0.3));

            batchArgs.add(new Object[]{
                advertiser.getId(), "[" + advertiser.getName() + "] 전략 광고 " + i,
                "https://cdn.ad-server.io/img/" + advertiser.getId() + "/" + i + ".jpg",
                "https://ad-server.io/click/" + i, maxBid, totalBudget, spentAmount,
                now.minusDays(random.nextInt(10)), "ACTIVE",
                gender, location, finalTags, "{}", now, now
            });

            if (batchArgs.size() >= batchSize) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
                batchArgs.clear();
            }
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }
        
        log.info("총 {}개의 [타입 안전한 페르소나] 광고 데이터가 완벽하게 인덱싱되었습니다.", TOTAL_ADS);
    }
}
