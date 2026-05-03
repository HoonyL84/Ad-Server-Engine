package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.user.profile.UserProfile;
import io.hoony.adserver.domain.user.profile.UserProfileClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class AdServingService {

    private final UserProfileClient userProfileClient;
    private final AdCandidateSearchService adCandidateSearchService;
    private final long dmpTimeoutMs;

    public AdServingService(
            UserProfileClient userProfileClient,
            AdCandidateSearchService adCandidateSearchService,
            @Value("${ad-server.serving.dmp-timeout-ms:30}") long dmpTimeoutMs
    ) {
        this.userProfileClient = userProfileClient;
        this.adCandidateSearchService = adCandidateSearchService;
        this.dmpTimeoutMs = dmpTimeoutMs;
    }

    public AdServingResult serve(String userId, String slotId) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Optional<UserProfile>> profileFuture =
                    executor.submit(() -> userProfileClient.getUserProfile(userId));
            Future<List<AdDocument>> candidatesFuture =
                    executor.submit(() -> adCandidateSearchService.searchCandidates(slotId));

            List<AdDocument> candidates = candidatesFuture.get();
            if (candidates.isEmpty()) {
                return new AdServingResult(null, true, ServingFallbackReason.NO_CANDIDATE);
            }

            ProfileResult profileResult = getProfileWithTimeout(profileFuture);

            if (profileResult.reason == ServingFallbackReason.NONE) {
                List<AdDocument> filtered = filterCandidates(candidates, profileResult.profile.orElseThrow());
                if (!filtered.isEmpty()) {
                    return new AdServingResult(filtered.get(0), false, ServingFallbackReason.NONE);
                }
                return new AdServingResult(candidates.get(0), true, ServingFallbackReason.TARGET_NOT_MATCHED);
            }

            return new AdServingResult(candidates.get(0), true, profileResult.reason);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Serving interrupted. userId={}, slotId={}", userId, slotId, e);
            return new AdServingResult(null, true, ServingFallbackReason.DMP_ERROR);
        } catch (ExecutionException e) {
            log.warn("Candidate search failed. userId={}, slotId={}", userId, slotId, e);
            return new AdServingResult(null, true, ServingFallbackReason.DMP_ERROR);
        }
    }

    private ProfileResult getProfileWithTimeout(Future<Optional<UserProfile>> profileFuture) {
        try {
            Optional<UserProfile> profile = profileFuture.get(dmpTimeoutMs, TimeUnit.MILLISECONDS);
            if (profile.isEmpty()) {
                return new ProfileResult(Optional.empty(), ServingFallbackReason.PROFILE_NOT_FOUND);
            }
            return new ProfileResult(profile, ServingFallbackReason.NONE);
        } catch (TimeoutException e) {
            profileFuture.cancel(true);
            return new ProfileResult(Optional.empty(), ServingFallbackReason.DMP_TIMEOUT);
        } catch (ExecutionException e) {
            return new ProfileResult(Optional.empty(), ServingFallbackReason.DMP_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProfileResult(Optional.empty(), ServingFallbackReason.DMP_ERROR);
        }
    }

    private List<AdDocument> filterCandidates(List<AdDocument> candidates, UserProfile profile) {
        return candidates.stream()
                .filter(ad -> genderMatches(ad.getTargetGender(), profile.gender()))
                .filter(ad -> locationMatches(ad.getTargetLocationId(), profile.locationId()))
                .filter(ad -> interestMatches(ad.getInterestTags(), profile.tags()))
                .toList();
    }

    private boolean genderMatches(String adGender, String userGender) {
        if (adGender == null || adGender.isBlank() || "ALL".equalsIgnoreCase(adGender)) {
            return true;
        }
        return adGender.equalsIgnoreCase(userGender);
    }

    private boolean locationMatches(String adLocation, String userLocation) {
        if (adLocation == null || adLocation.isBlank() || "0".equals(adLocation)) {
            return true;
        }
        return adLocation.equals(userLocation);
    }

    private boolean interestMatches(List<String> adTags, List<String> userTags) {
        if (adTags == null || adTags.isEmpty()) {
            return true;
        }
        if (userTags == null || userTags.isEmpty()) {
            return false;
        }
        return adTags.stream().anyMatch(tag ->
                userTags.stream().anyMatch(userTag -> userTag.equalsIgnoreCase(tag)));
    }

    private record ProfileResult(Optional<UserProfile> profile, ServingFallbackReason reason) {
    }
}
