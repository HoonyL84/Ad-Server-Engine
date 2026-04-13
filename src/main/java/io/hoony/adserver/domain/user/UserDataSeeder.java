package io.hoony.adserver.domain.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [Infrastructure Mock] DMP(Data Management Platform) 시뮬레이터
 * 
 * [Architecture Note]
 * 이 클래스는 광고 서버의 도메인 로직이 아닙니다. Step 5에서 진행할 
 * "gRPC 기반 유저 프로필 조회"를 실전과 같은 규모(10만 명)로 테스트하기 위해 
 * 가상의 DMP 저장소(Redis)에 기초 데이터를 채워주는 [인프라 구축용] 도구입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDataSeeder implements CommandLineRunner {

    private final StringRedisTemplate redisTemplate;

    private static final int TOTAL_USERS = 100000;
    private static final String USER_KEY_PREFIX = "user:profile:";

    @Override
    public void run(@SuppressWarnings("unused") String... args) {
        // 1. 멱등성 체크 (데이터가 이미 존재하면 스킵)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(USER_KEY_PREFIX + TOTAL_USERS))) {
            log.info("[DMP Mock] 이미 10만 명의 유저 프로필이 준비되어 있습니다. 시딩을 중단합니다.");
            return;
        }

        log.info("[DMP Mock] 가상 DMP(Redis)에 10만 명의 페르소나 적재를 시작합니다...");
        long startTime = System.currentTimeMillis();

        // 2. Redis Pipelining을 통한 최적화된 벌크 인서트
        // [Master Fix] executePipelined의 Ambiguous 에러 해결을 위해 RedisCallback으로 명시적 캐스팅
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            List<String> genders = Arrays.asList("M", "F", "ALL");
            List<String> locations = Arrays.asList("1:11", "1:12", "1:13", "1:14", "2:21", "2:22", "3:31", "4:41", "5:51", "9:99", "0");
            List<String> interestPool = Arrays.asList("패션", "뷰티", "신발", "운동", "로컬", "맛집", "반려동물", "가구", "인테리어", "캠핑", "금융", "게임", "자동차");

            for (int i = 1; i <= TOTAL_USERS; i++) {
                String userId = String.valueOf(i);
                String gender = genders.get(random.nextInt(genders.size()));
                String locationId = (random.nextInt(100) < 75) ? "0" : locations.get(random.nextInt(locations.size()));
                
                int tagCount = random.nextInt(3) + 1;
                StringBuilder tags = new StringBuilder();
                for (int j = 0; j < tagCount; j++) {
                    if (j > 0) tags.append(",");
                    tags.append(interestPool.get(random.nextInt(interestPool.size())));
                }

                String jsonProfile = String.format(
                    "{\"user_id\":\"%s\",\"gender\":\"%s\",\"location_id\":\"%s\",\"tags\":\"%s\"}",
                    userId, gender, locationId, tags.toString()
                );

                byte[] key = (USER_KEY_PREFIX + userId).getBytes();
                byte[] value = jsonProfile.getBytes();
                
                // [Standard Fix] Deprecated 된 set 대신 stringCommands()를 통해 최신 표준 준수
                connection.stringCommands().set(key, value);
            }
            return null;
        });

        long duration = System.currentTimeMillis() - startTime;
        log.info("[DMP Mock] 10만 유저 프로필 적재 완료 (소요 시간: {}ms)", duration);
    }
}
