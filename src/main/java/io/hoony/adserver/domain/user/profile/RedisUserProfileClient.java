package io.hoony.adserver.domain.user.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUserProfileClient implements UserProfileClient {

    private static final String USER_KEY_PREFIX = "user:profile:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<UserProfile> getUserProfile(String userId) {
        String key = USER_KEY_PREFIX + userId;
        String payload = redisTemplate.opsForValue().get(key);

        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode node = objectMapper.readTree(payload);
            return Optional.of(new UserProfile(
                    node.path("user_id").asText(userId),
                    node.path("gender").asText("ALL"),
                    node.path("location_id").asText("0"),
                    parseAge(node.path("age")),
                    parseTags(node.path("tags"))
            ));
        } catch (Exception e) {
            log.warn("Failed to parse user profile payload. key={}", key, e);
            return Optional.empty();
        }
    }

    private Integer parseAge(JsonNode ageNode) {
        if (ageNode.isMissingNode() || ageNode.isNull()) {
            return null;
        }
        if (ageNode.isInt() || ageNode.isLong()) {
            return ageNode.asInt();
        }
        String raw = ageNode.asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> parseTags(JsonNode tagNode) {
        if (tagNode.isMissingNode() || tagNode.isNull()) {
            return List.of();
        }

        if (tagNode.isArray()) {
            List<String> tags = new ArrayList<>();
            tagNode.forEach(t -> tags.add(t.asText().trim()));
            return tags;
        }

        String raw = tagNode.asText();
        if (raw.isBlank()) {
            return List.of();
        }

        String[] split = raw.split(",");
        List<String> tags = new ArrayList<>(split.length);
        for (String tag : split) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }
}
