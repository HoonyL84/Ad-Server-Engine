package io.hoony.adserver.domain.ad;

import io.hoony.adserver.domain.advertiser.Advertiser;
import io.hoony.adserver.domain.advertiser.AdvertiserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@ConditionalOnProperty(name = "ad-server.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AdDataSeeder implements CommandLineRunner {

    private static final int TOTAL_ADS = 3000;
    private static final int BATCH_SIZE = 1000;

    private static final List<String> LOCATION_POOL = List.of(
            "1:11", "1:12", "1:13", "1:14", "2:21", "2:22", "3:31", "4:41", "5:51", "9:99", "0"
    );

    private static final List<String> GENDER_POOL = List.of("M", "F", "ALL");
    private static final List<String> GENERAL_TAGS = List.of(
            "finance", "game", "car", "health", "book", "movie", "music", "coin", "pet", "food"
    );

    private static final Map<PersonaType, List<String>> PERSONA_TAGS = Map.of(
            PersonaType.FASHION, List.of("fashion", "beauty", "shoes", "sports", "accessory"),
            PersonaType.LOCAL, List.of("local", "used", "part-time", "restaurant", "pet"),
            PersonaType.HOME, List.of("furniture", "interior", "living", "lighting", "camping")
    );

    private final AdRepository adRepository;
    private final AdvertiserRepository advertiserRepository;
    private final JdbcTemplate jdbcTemplate;

    private enum PersonaType {
        FASHION, LOCAL, HOME, GENERAL
    }

    @Override
    @Transactional
    public void run(@SuppressWarnings("unused") String... args) {
        if (adRepository.count() >= TOTAL_ADS) {
            log.info("Ad seed skipped. already has {} or more ads.", TOTAL_ADS);
            return;
        }

        List<Advertiser> advertisers = advertiserRepository.findAll();
        if (advertisers.isEmpty()) {
            log.warn("Ad seed skipped. no advertisers found.");
            return;
        }

        log.info("Starting ad seed. targetCount={}", TOTAL_ADS);

        String sql = """
                INSERT INTO ad
                (advertiser_id, title, image_url, click_url, max_bid, total_budget, spent_amount, start_date, status,
                 target_gender, target_location_id, target_interest_tags, target_context, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> batchArgs = new ArrayList<>(BATCH_SIZE);

        for (int i = 1; i <= TOTAL_ADS; i++) {
            Advertiser advertiser = advertisers.get(random.nextInt(advertisers.size()));
            PersonaType persona = personaOf(advertiser);
            BigDecimal totalBudget = randomTotalBudget(random);

            batchArgs.add(new Object[]{
                    advertiser.getId(),
                    "[" + advertiser.getName() + "] strategy ad " + i,
                    "https://cdn.ad-server.io/img/" + advertiser.getId() + "/" + i + ".jpg",
                    "https://ad-server.io/click/" + i,
                    randomBid(random),
                    totalBudget,
                    randomSpentAmount(random, totalBudget),
                    now.minusDays(random.nextInt(10)),
                    "ACTIVE",
                    randomGender(random, persona),
                    randomLocation(random, persona),
                    randomTags(random, persona),
                    "{}",
                    now,
                    now
            });

            if (batchArgs.size() >= BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
                batchArgs.clear();
            }
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }

        log.info("Ad seed completed. targetCount={}", TOTAL_ADS);
    }

    private PersonaType personaOf(Advertiser advertiser) {
        String name = advertiser.getName();
        if (name.contains("패션")) {
            return PersonaType.FASHION;
        }
        if (name.contains("로컬")) {
            return PersonaType.LOCAL;
        }
        if (name.contains("홈")) {
            return PersonaType.HOME;
        }
        return PersonaType.GENERAL;
    }

    private BigDecimal randomBid(ThreadLocalRandom random) {
        int range = random.nextInt(100);
        if (range < 10) {
            return BigDecimal.valueOf(random.nextLong(1001) + 4000);
        }
        if (range < 70) {
            return BigDecimal.valueOf(random.nextLong(2001) + 1000);
        }
        return BigDecimal.valueOf(random.nextLong(401) + 100);
    }

    private BigDecimal randomTotalBudget(ThreadLocalRandom random) {
        return BigDecimal.valueOf((random.nextLong(100) + 1) * 100000);
    }

    private BigDecimal randomSpentAmount(ThreadLocalRandom random, BigDecimal totalBudget) {
        if (random.nextInt(100) < 5) {
            return totalBudget.subtract(BigDecimal.valueOf(random.nextInt(100)));
        }
        return totalBudget.multiply(BigDecimal.valueOf(random.nextDouble() * 0.3));
    }

    private String randomGender(ThreadLocalRandom random, PersonaType persona) {
        if (persona == PersonaType.FASHION) {
            return random.nextBoolean() ? "F" : "M";
        }
        return GENDER_POOL.get(random.nextInt(GENDER_POOL.size()));
    }

    private String randomLocation(ThreadLocalRandom random, PersonaType persona) {
        if (persona == PersonaType.LOCAL) {
            return random.nextInt(100) < 60 ? "1:11" : LOCATION_POOL.get(random.nextInt(LOCATION_POOL.size()));
        }
        return LOCATION_POOL.get(random.nextInt(LOCATION_POOL.size()));
    }

    private String randomTags(ThreadLocalRandom random, PersonaType persona) {
        List<String> preferredTags = PERSONA_TAGS.getOrDefault(persona, GENERAL_TAGS);
        List<String> tags = new ArrayList<>();
        int tagCount = random.nextInt(3) + 1;

        for (int i = 0; i < tagCount; i++) {
            List<String> source = random.nextInt(100) < 85 ? preferredTags : GENERAL_TAGS;
            tags.add(source.get(random.nextInt(source.size())));
        }

        return String.join(",", new HashSet<>(tags));
    }
}
