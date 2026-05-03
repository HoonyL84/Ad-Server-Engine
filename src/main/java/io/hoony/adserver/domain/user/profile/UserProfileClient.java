package io.hoony.adserver.domain.user.profile;

import java.util.Optional;

public interface UserProfileClient {
    Optional<UserProfile> getUserProfile(String userId);
}
