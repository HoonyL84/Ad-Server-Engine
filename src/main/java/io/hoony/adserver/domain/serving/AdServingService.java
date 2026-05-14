package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.user.profile.UserProfile;
import io.hoony.adserver.domain.user.profile.UserProfileClient;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class AdServingService {

    private final UserProfileClient userProfileClient;
    private final AdCandidateSearchService adCandidateSearchService;
    private final AdMatcher adMatcher;
    private final AdRanker adRanker;
    private final AdBudgetService adBudgetService;
    private final AdServingMetrics adServingMetrics;
    private final ExecutorService executorService;
    private final long dmpTimeoutMs;
    private final long candidateTimeoutMs;

    public AdServingService(
            UserProfileClient userProfileClient,
            AdCandidateSearchService adCandidateSearchService,
            AdMatcher adMatcher,
            AdRanker adRanker,
            AdBudgetService adBudgetService,
            AdServingMetrics adServingMetrics,
            ExecutorService executorService,
            @Value("${ad-server.serving.dmp-timeout-ms:30}") long dmpTimeoutMs,
            @Value("${ad-server.serving.candidate-timeout-ms:50}") long candidateTimeoutMs
    ) {
        this.userProfileClient = userProfileClient;
        this.adCandidateSearchService = adCandidateSearchService;
        this.adMatcher = adMatcher;
        this.adRanker = adRanker;
        this.adBudgetService = adBudgetService;
        this.adServingMetrics = adServingMetrics;
        this.executorService = executorService;
        this.dmpTimeoutMs = dmpTimeoutMs;
        this.candidateTimeoutMs = candidateTimeoutMs;
    }

    public AdServingResult serve(String userId, String slotId) {
        Timer.Sample sample = adServingMetrics.startTimer();
        AdServingResult result = doServe(userId, slotId);
        adServingMetrics.record(slotId, result, sample);
        return result;
    }

    private AdServingResult doServe(String userId, String slotId) {
        try {
            Future<Optional<UserProfile>> profileFuture =
                    executorService.submit(() -> userProfileClient.getUserProfile(userId));
            Future<List<AdDocument>> candidatesFuture =
                    executorService.submit(() -> adCandidateSearchService.searchCandidates(slotId));

            CandidateResult candidateResult = getCandidatesWithTimeout(candidatesFuture);
            if (candidateResult.reason.isPresent()) {
                return new AdServingResult(null, true, candidateResult.reason.get(), 0, 0);
            }

            List<AdDocument> candidates = candidateResult.candidates;
            if (candidates.isEmpty()) {
                return new AdServingResult(null, true, ServingFallbackReason.NO_CANDIDATE, 0, 0);
            }

            ProfileResult profileResult = getProfileWithTimeout(profileFuture);

            if (profileResult.reason == ServingFallbackReason.NONE) {
                List<AdDocument> filtered = adMatcher.match(candidates, profileResult.profile.orElseThrow());
                if (!filtered.isEmpty()) {
                    return selectSpendableAd(filtered, false, ServingFallbackReason.NONE, candidates.size(), filtered.size());
                }
                return selectSpendableAd(candidates, true, ServingFallbackReason.TARGET_NOT_MATCHED, candidates.size(), 0);
            }

            return selectSpendableAd(candidates, true, profileResult.reason, candidates.size(), 0);
        } catch (RuntimeException e) {
            log.warn("Serving failed unexpectedly. userId={}, slotId={}", userId, slotId, e);
            return new AdServingResult(null, true, ServingFallbackReason.CANDIDATE_ERROR, 0, 0);
        }
    }

    private AdServingResult selectSpendableAd(
            List<AdDocument> candidates,
            boolean fallback,
            ServingFallbackReason fallbackReason,
            int candidateCount,
            int matchedCount
    ) {
        return adRanker.rank(candidates).stream()
                .filter(ad -> !adBudgetService.isExhausted(ad))
                .filter(adBudgetService::trySpend)
                .findFirst()
                .map(ad -> new AdServingResult(ad, fallback, fallbackReason, candidateCount, matchedCount))
                .orElseGet(() -> new AdServingResult(
                        null,
                        true,
                        ServingFallbackReason.BUDGET_EXHAUSTED,
                        candidateCount,
                        matchedCount
                ));
    }

    private CandidateResult getCandidatesWithTimeout(Future<List<AdDocument>> candidatesFuture) {
        try {
            return new CandidateResult(
                    candidatesFuture.get(candidateTimeoutMs, TimeUnit.MILLISECONDS),
                    Optional.empty()
            );
        } catch (TimeoutException e) {
            candidatesFuture.cancel(true);
            return new CandidateResult(List.of(), Optional.of(ServingFallbackReason.CANDIDATE_TIMEOUT));
        } catch (ExecutionException e) {
            return new CandidateResult(List.of(), Optional.of(ServingFallbackReason.CANDIDATE_ERROR));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CandidateResult(List.of(), Optional.of(ServingFallbackReason.CANDIDATE_ERROR));
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

    private record ProfileResult(Optional<UserProfile> profile, ServingFallbackReason reason) {
    }

    private record CandidateResult(List<AdDocument> candidates, Optional<ServingFallbackReason> reason) {
    }
}
