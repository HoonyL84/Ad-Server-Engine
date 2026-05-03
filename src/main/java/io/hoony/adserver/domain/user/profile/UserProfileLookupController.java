package io.hoony.adserver.domain.user.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/dmp")
public class UserProfileLookupController {

    private final UserProfileClient userProfileClient;

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserProfile> getProfile(@PathVariable String userId) {
        return userProfileClient.getUserProfile(userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
