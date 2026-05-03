package io.hoony.adserver.domain.user.profile;

import java.util.List;

public record UserProfile(
        String userId,
        String gender,
        String locationId,
        Integer age,
        List<String> tags
) {
}
