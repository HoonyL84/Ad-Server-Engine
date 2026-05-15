package io.hoony.adserver.domain.user.profile;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.hoony.adserver.grpc.UserProfileServiceGrpc;
import io.hoony.adserver.grpc.UserRequest;
import io.hoony.adserver.grpc.UserResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Primary
@Component
@ConditionalOnProperty(name = "ad-server.dmp.provider", havingValue = "grpc", matchIfMissing = true)
public class GrpcUserProfileClient implements UserProfileClient {

    private final ManagedChannel channel;
    private final UserProfileServiceGrpc.UserProfileServiceBlockingStub blockingStub;
    private final long requestTimeoutMs;

    public GrpcUserProfileClient(
            @Value("${ad-server.dmp.grpc.host:localhost}") String host,
            @Value("${ad-server.dmp.grpc.port:9090}") int port,
            @Value("${ad-server.serving.dmp-timeout-ms:30}") long requestTimeoutMs
    ) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = UserProfileServiceGrpc.newBlockingStub(channel);
        this.requestTimeoutMs = requestTimeoutMs;
    }

    @Override
    public Optional<UserProfile> getUserProfile(String userId) {
        UserRequest request = UserRequest.newBuilder()
                .setUserId(userId)
                .build();

        try {
            UserResponse response = blockingStub
                    .withDeadlineAfter(requestTimeoutMs, TimeUnit.MILLISECONDS)
                    .getUserProfile(request);

            if (response.getUserId() == null || response.getUserId().isBlank()) {
                return Optional.empty();
            }

            List<String> tags = response.getTagsList();
            return Optional.of(new UserProfile(
                    response.getUserId(),
                    response.getGender().isBlank() ? "ALL" : response.getGender(),
                    response.getLocationId().isBlank() ? "0" : response.getLocationId(),
                    null,
                    tags
            ));
        } catch (StatusRuntimeException e) {
            throw e;
        }
    }

    @PreDestroy
    void shutdown() {
        channel.shutdown();
    }
}
