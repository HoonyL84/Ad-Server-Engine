package io.hoony.adserver.domain.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@ConditionalOnProperty(name = "ad-server.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class UserDataSeeder implements CommandLineRunner {

    private static final int TOTAL_USERS = 100000;
    private static final String USER_KEY_PREFIX = "user:profile:";

    private static final List<String> GENDER_POOL = List.of("M", "F", "ALL");
    private static final List<String> LOCATION_POOL = List.of(
            "1:11", "1:12", "1:13", "1:14", "2:21", "2:22", "3:31", "4:41", "5:51", "9:99", "0"
    );
    private static final List<String> INTEREST_POOL = List.of(
            "fashion", "beauty", "shoes", "sports", "accessory",
            "local", "used", "part-time", "restaurant", "pet",
            "furniture", "interior", "living", "lighting", "camping",
            "finance", "game", "car", "health", "book", "movie", "music", "coin", "food"
    );

    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(@SuppressWarnings("unused") String... args) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(USER_KEY_PREFIX + TOTAL_USERS))) {
            log.info("User profile seed skipped. already has {} users.", TOTAL_USERS);
            return;
        }

        log.info("Starting user profile seed. targetCount={}", TOTAL_USERS);
        long startTime = System.currentTimeMillis();

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();

            for (int i = 1; i <= TOTAL_USERS; i++) {
                String userId = String.valueOf(i);
                String gender = GENDER_POOL.get(random.nextInt(GENDER_POOL.size()));
                String locationId = random.nextInt(100) < 75 ? "0" : LOCATION_POOL.get(random.nextInt(LOCATION_POOL.size()));
                int age = random.nextInt(18, 60);
                String tags = randomTags(random);

                String jsonProfile = """
                        {"user_id":"%s","gender":"%s","location_id":"%s","age":%d,"tags":"%s"}
                        """.formatted(userId, gender, locationId, age, tags).trim();

                byte[] key = (USER_KEY_PREFIX + userId).getBytes(StandardCharsets.UTF_8);
                byte[] value = jsonProfile.getBytes(StandardCharsets.UTF_8);

                connection.stringCommands().set(key, value);
            }
            return null;
        });

        long duration = System.currentTimeMillis() - startTime;
        log.info("User profile seed completed. targetCount={}, durationMs={}", TOTAL_USERS, duration);
    }

    private String randomTags(ThreadLocalRandom random) {
        int tagCount = random.nextInt(3) + 1;
        Set<String> tags = new LinkedHashSet<>();
        while (tags.size() < tagCount) {
            tags.add(INTEREST_POOL.get(random.nextInt(INTEREST_POOL.size())));
        }
        return String.join(",", tags);
    }
}
