package io.hoony.adserver.domain.user.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.hoony.adserver.grpc.UserProfileServiceGrpc;
import io.hoony.adserver.grpc.UserRequest;
import io.hoony.adserver.grpc.UserResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "ad-server.dmp.grpc.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockDmpGrpcServer implements InitializingBean {

    private static final String USER_KEY_PREFIX = "user:profile:";

    private final int grpcPort;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private Server server;

    public MockDmpGrpcServer(
            @Value("${ad-server.dmp.grpc.port:9090}") int grpcPort,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.grpcPort = grpcPort;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        server = ServerBuilder.forPort(grpcPort)
                .addService(new UserProfileServiceImpl())
                .build()
                .start();
        log.info("Mock DMP gRPC server started. port={}", grpcPort);
    }

    @PreDestroy
    void stop() {
        if (server != null) {
            server.shutdown();
            log.info("Mock DMP gRPC server stopped.");
        }
    }

    private class UserProfileServiceImpl extends UserProfileServiceGrpc.UserProfileServiceImplBase {

        @Override
        public void getUserProfile(UserRequest request, StreamObserver<UserResponse> responseObserver) {
            String userId = request.getUserId();
            String key = USER_KEY_PREFIX + userId;
            String payload = redisTemplate.opsForValue().get(key);

            if (payload == null || payload.isBlank()) {
                responseObserver.onNext(UserResponse.newBuilder().build());
                responseObserver.onCompleted();
                return;
            }

            try {
                JsonNode node = objectMapper.readTree(payload);
                UserResponse.Builder builder = UserResponse.newBuilder()
                        .setUserId(node.path("user_id").asText(userId))
                        .setGender(node.path("gender").asText("ALL"))
                        .setLocationId(node.path("location_id").asText("0"));

                for (String tag : parseTags(node.path("tags"))) {
                    builder.addTags(tag);
                }

                responseObserver.onNext(builder.build());
                responseObserver.onCompleted();
            } catch (Exception e) {
                responseObserver.onError(e);
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
}
