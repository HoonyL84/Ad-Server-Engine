package io.hoony.adserver.domain.openrtb;

import io.hoony.adserver.config.MdcTraceUtils;
import io.hoony.adserver.config.TracingSupport;
import io.hoony.adserver.domain.ad.search.AdDocument;
import io.hoony.adserver.domain.openrtb.dto.Bid;
import io.hoony.adserver.domain.openrtb.dto.BidRequest;
import io.hoony.adserver.domain.openrtb.dto.BidResponse;
import io.hoony.adserver.domain.openrtb.dto.Imp;
import io.hoony.adserver.domain.openrtb.dto.SeatBid;
import io.hoony.adserver.domain.serving.AdBudgetService;
import io.hoony.adserver.domain.serving.AdCandidateSearchService;
import io.hoony.adserver.domain.serving.AdMatcher;
import io.hoony.adserver.domain.serving.AdRanker;
import io.hoony.adserver.domain.serving.AdSlotMatcher;
import io.hoony.adserver.domain.serving.SimpleCircuitBreaker;
import io.hoony.adserver.domain.user.profile.UserProfile;
import io.hoony.adserver.domain.user.profile.UserProfileClient;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OpenRTB 2.x DSP Bidder 서비스 (Step 18-B).
 *
 * <p>기존 {@code AdServingService}(1st-party 서빙, 30ms/50ms timeout 및 broad fallback으로 fill 우선)와 달리,
 * 15ms SLA critical path 안에서 {@code slotId} / {@code bidfloor} / 타겟 / 예산 조건을 만족할 때만
 * 입찰({@link BidResponse})을 반환하고 미충족 시 즉시 No-Bid(HTTP 204)로 fail-closed 처리한다.
 */
@Slf4j
@Service
public class OpenRtbBidderService {

    private final UserProfileClient userProfileClient;
    private final AdCandidateSearchService adCandidateSearchService;
    private final AdSlotMatcher adSlotMatcher;
    private final AdMatcher adMatcher;
    private final AdRanker adRanker;
    private final AdBudgetService adBudgetService;
    private final OpenRtbMetrics openRtbMetrics;
    private final OpenRtbTrackingUrlBuilder trackingUrlBuilder;
    private final ExecutorService executorService;
    private final SimpleCircuitBreaker simpleCircuitBreaker;
    private final TracingSupport tracingSupport;
    private final long slaTimeoutMs;
    private final long dmpTimeoutMs;
    private final String seatId;
    private final String defaultCurrency;

    public OpenRtbBidderService(
            UserProfileClient userProfileClient,
            AdCandidateSearchService adCandidateSearchService,
            AdSlotMatcher adSlotMatcher,
            AdMatcher adMatcher,
            Map<String, AdRanker> adRankers,
            @Value("${ad-server.openrtb.ranking-strategy:max-bid}") String rankingStrategy,
            AdBudgetService adBudgetService,
            OpenRtbMetrics openRtbMetrics,
            OpenRtbTrackingUrlBuilder trackingUrlBuilder,
            ExecutorService executorService,
            SimpleCircuitBreaker simpleCircuitBreaker,
            TracingSupport tracingSupport,
            @Value("${ad-server.openrtb.sla-timeout-ms:15}") long slaTimeoutMs,
            @Value("${ad-server.openrtb.dmp-timeout-ms:8}") long dmpTimeoutMs,
            @Value("${ad-server.openrtb.seat-id:ad-server-engine}") String seatId,
            @Value("${ad-server.openrtb.default-currency:KRW}") String defaultCurrency
    ) {
        this.userProfileClient = userProfileClient;
        this.adCandidateSearchService = adCandidateSearchService;
        this.adSlotMatcher = adSlotMatcher;
        this.adMatcher = adMatcher;
        this.adRanker = adRankers.get(rankingStrategy);
        if (this.adRanker == null) {
            throw new IllegalArgumentException("Unknown OpenRTB ranking strategy: " + rankingStrategy);
        }
        this.adBudgetService = adBudgetService;
        this.openRtbMetrics = openRtbMetrics;
        this.trackingUrlBuilder = trackingUrlBuilder;
        this.executorService = executorService;
        this.simpleCircuitBreaker = simpleCircuitBreaker;
        this.tracingSupport = tracingSupport;
        this.slaTimeoutMs = Math.max(slaTimeoutMs, 1L);
        this.dmpTimeoutMs = Math.max(dmpTimeoutMs, 1L);
        this.seatId = seatId;
        this.defaultCurrency = defaultCurrency;
    }

    public OpenRtbBidResult bid(BidRequest request) {
        String primarySlot = extractPrimarySlot(request);
        return tracingSupport.observe("openrtb.bid.request", "slot.id", primarySlot, () -> {
            Timer.Sample sample = openRtbMetrics.startTimer();
            OpenRtbBidResult result = doBid(request);
            openRtbMetrics.recordBid(primarySlot, result, sample);
            return result;
        });
    }

    private OpenRtbBidResult doBid(BidRequest request) {
        if (!isValidRequest(request)) {
            return OpenRtbBidResult.noBid(NoBidReason.INVALID_REQUEST);
        }

        long effectiveSlaMs = resolveEffectiveSlaMs(request.tmax());
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(effectiveSlaMs);
        String userId = extractUserId(request);

        if (userId != null && !simpleCircuitBreaker.allowRequest()) {
            log.debug("OpenRTB DMP blocked by circuit breaker. requestId={}, userId={}", request.id(), userId);
            return OpenRtbBidResult.noBid(NoBidReason.DMP_CIRCUIT_OPEN);
        }

        Future<Optional<UserProfile>> profileFuture = submitProfileLookup(userId);
        ProfileOutcome cachedProfileOutcome = null;
        List<Bid> bids = new ArrayList<>();
        NoBidReason lastReason = NoBidReason.NO_CANDIDATE;

        try {
            for (Imp imp : request.imp()) {
                if (!isValidImp(imp)) {
                    return OpenRtbBidResult.noBid(NoBidReason.INVALID_REQUEST);
                }

                String slotId = imp.tagid();
                if (isBlank(slotId)) {
                    lastReason = NoBidReason.SLOT_UNMATCHED;
                    continue;
                }

                if (remainingNanos(deadlineNanos) <= 0L) {
                    lastReason = NoBidReason.SLA_TIMEOUT;
                    break;
                }

                CandidateOutcome candidateOutcome = fetchCandidatesWithinSla(slotId, deadlineNanos);
                if (candidateOutcome.reason() != NoBidReason.NONE) {
                    lastReason = candidateOutcome.reason();
                    if (lastReason == NoBidReason.SLA_TIMEOUT) {
                        break;
                    }
                    continue;
                }

                List<AdDocument> floorCandidates = matchSlotAndFloor(candidateOutcome.candidates(), slotId, imp.normalizedBidFloor());
                if (floorCandidates.isEmpty()) {
                    lastReason = hasSlotMatch(candidateOutcome.candidates(), slotId)
                            ? NoBidReason.BELOW_FLOOR
                            : NoBidReason.SLOT_UNMATCHED;
                    continue;
                }

                List<AdDocument> eligibleCandidates = floorCandidates;
                if (userId != null) {
                    if (cachedProfileOutcome == null) {
                        cachedProfileOutcome = awaitProfileWithinSla(profileFuture, deadlineNanos);
                    }
                    if (cachedProfileOutcome.reason() != NoBidReason.NONE) {
                        lastReason = cachedProfileOutcome.reason();
                        break;
                    }
                    UserProfile resolvedProfile = cachedProfileOutcome.profile();
                    eligibleCandidates = tracingSupport.observe("openrtb.target.match", "slot.id", slotId, () ->
                            adMatcher.match(floorCandidates, resolvedProfile));
                    if (eligibleCandidates.isEmpty()) {
                        lastReason = NoBidReason.TARGET_NOT_MATCHED;
                        continue;
                    }
                }

                if (remainingNanos(deadlineNanos) <= 0L) {
                    lastReason = NoBidReason.SLA_TIMEOUT;
                    break;
                }

                Optional<AdDocument> selected = selectSpendableBid(eligibleCandidates, imp.normalizedBidFloor());
                if (selected.isEmpty()) {
                    lastReason = NoBidReason.BUDGET_EXHAUSTED;
                    continue;
                }

                bids.add(toBid(request.id(), imp, selected.get(), userId));
            }
        } catch (RuntimeException e) {
            log.warn("OpenRTB bid failed unexpectedly. requestId={}", request.id(), e);
            return OpenRtbBidResult.noBid(NoBidReason.CANDIDATE_ERROR);
        } finally {
            if (profileFuture != null && !profileFuture.isDone()) {
                profileFuture.cancel(true);
            }
        }

        if (bids.isEmpty()) {
            log.debug("OpenRTB No-Bid. requestId={}, reason={}", request.id(), lastReason);
            return OpenRtbBidResult.noBid(lastReason);
        }

        String bidResponseId = UUID.randomUUID().toString();
        String currency = resolveCurrency(request);
        BidResponse response = new BidResponse(
                request.id(),
                List.of(new SeatBid(List.copyOf(bids), seatId)),
                bidResponseId,
                currency
        );
        return OpenRtbBidResult.bid(response);
    }

    private Future<Optional<UserProfile>> submitProfileLookup(String userId) {
        if (userId == null) {
            return null;
        }
        return executorService.submit(MdcTraceUtils.withTraceContext(() ->
                tracingSupport.observe("openrtb.dmp.profile", "dmp.provider", "grpc", () ->
                        userProfileClient.getUserProfile(userId))));
    }

    private CandidateOutcome fetchCandidatesWithinSla(String slotId, long deadlineNanos) {
        long waitNanos = remainingNanos(deadlineNanos);
        if (waitNanos <= 0L) {
            return CandidateOutcome.fail(NoBidReason.SLA_TIMEOUT);
        }

        Future<List<AdDocument>> future = executorService.submit(MdcTraceUtils.withTraceContext(() ->
                tracingSupport.observe("openrtb.candidate.lookup", "slot.id", slotId, () ->
                        adCandidateSearchService.searchCandidates(slotId))));

        try {
            List<AdDocument> candidates = future.get(waitNanos, TimeUnit.NANOSECONDS);
            if (candidates == null || candidates.isEmpty()) {
                return CandidateOutcome.fail(NoBidReason.NO_CANDIDATE);
            }
            return CandidateOutcome.ok(candidates);
        } catch (TimeoutException e) {
            future.cancel(true);
            return CandidateOutcome.fail(NoBidReason.CANDIDATE_TIMEOUT);
        } catch (ExecutionException e) {
            return CandidateOutcome.fail(NoBidReason.CANDIDATE_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return CandidateOutcome.fail(NoBidReason.CANDIDATE_ERROR);
        }
    }

    private ProfileOutcome awaitProfileWithinSla(Future<Optional<UserProfile>> profileFuture, long deadlineNanos) {
        long remaining = remainingNanos(deadlineNanos);
        if (remaining <= 0L) {
            profileFuture.cancel(true);
            return ProfileOutcome.fail(NoBidReason.SLA_TIMEOUT);
        }

        long waitNanos = Math.min(TimeUnit.MILLISECONDS.toNanos(dmpTimeoutMs), remaining);
        try {
            Optional<UserProfile> profile = profileFuture.get(waitNanos, TimeUnit.NANOSECONDS);
            if (profile == null || profile.isEmpty()) {
                return ProfileOutcome.fail(NoBidReason.PROFILE_NOT_FOUND);
            }
            return ProfileOutcome.ok(profile.get());
        } catch (TimeoutException e) {
            // 15ms SLA 전용 타임아웃은 1st-party AdServingService(30ms) 서킷 브레이커 상태를 오염시키지 않는다.
            profileFuture.cancel(true);
            return ProfileOutcome.fail(NoBidReason.DMP_TIMEOUT);
        } catch (ExecutionException e) {
            return ProfileOutcome.fail(NoBidReason.DMP_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            profileFuture.cancel(true);
            return ProfileOutcome.fail(NoBidReason.DMP_ERROR);
        }
    }

    private List<AdDocument> matchSlotAndFloor(List<AdDocument> candidates, String slotId, BigDecimal floorPrice) {
        return tracingSupport.observe("openrtb.slot.floor.match", "slot.id", slotId, () -> {
            List<AdDocument> slotMatched = adSlotMatcher.match(candidates, slotId);
            if (slotMatched.isEmpty()) {
                return List.of();
            }
            return slotMatched.stream()
                    .filter(ad -> adSlotMatcher.isAboveFloorPrice(ad, floorPrice))
                    .toList();
        });
    }

    private boolean hasSlotMatch(List<AdDocument> candidates, String slotId) {
        return !adSlotMatcher.match(candidates, slotId).isEmpty();
    }

    private Optional<AdDocument> selectSpendableBid(List<AdDocument> candidates, BigDecimal floorPrice) {
        List<AdDocument> ranked = tracingSupport.observe("openrtb.ranking", () -> adRanker.rank(candidates));
        List<AdDocument> spendable = tracingSupport.observe("openrtb.budget.filter", () ->
                adBudgetService.filterExhausted(ranked));

        return spendable.stream()
                .filter(ad -> adSlotMatcher.isAboveFloorPrice(ad, floorPrice))
                .filter(adBudgetService::trySpend)
                .findFirst();
    }

    private Bid toBid(String requestId, Imp imp, AdDocument ad, String userId) {
        String bidId = UUID.randomUUID().toString();
        String slotId = imp.tagid();
        String adIdStr = String.valueOf(ad.getId());

        return new Bid(
                bidId,
                imp.id(),
                ad.getMaxBid(),
                adIdStr,
                trackingUrlBuilder.buildWinUrl(bidId, requestId, imp.id(), ad.getId(), slotId, userId),
                trackingUrlBuilder.buildLossUrl(bidId, requestId, imp.id(), ad.getId(), slotId, userId),
                trackingUrlBuilder.buildAdm(ad, bidId, requestId, userId, slotId),
                adIdStr
        );
    }

    private boolean isValidRequest(BidRequest request) {
        return request != null
                && !isBlank(request.id())
                && request.imp() != null
                && !request.imp().isEmpty();
    }

    private boolean isValidImp(Imp imp) {
        return imp != null
                && !isBlank(imp.id())
                && imp.normalizedBidFloor().compareTo(BigDecimal.ZERO) >= 0;
    }

    private long resolveEffectiveSlaMs(Integer tmax) {
        if (tmax != null && tmax > 0) {
            return Math.min(slaTimeoutMs, tmax);
        }
        return slaTimeoutMs;
    }

    private long remainingNanos(long deadlineNanos) {
        return Math.max(0L, deadlineNanos - System.nanoTime());
    }

    private String extractUserId(BidRequest request) {
        if (request.user() == null || isBlank(request.user().id())) {
            return null;
        }
        return request.user().id().trim();
    }

    private String extractPrimarySlot(BidRequest request) {
        if (request == null || request.imp() == null || request.imp().isEmpty()) {
            return "unknown";
        }
        String tagid = request.imp().get(0).tagid();
        return isBlank(tagid) ? "unknown" : tagid.trim();
    }

    private String resolveCurrency(BidRequest request) {
        if (request.cur() != null && !request.cur().isEmpty() && !isBlank(request.cur().get(0))) {
            return request.cur().get(0).trim();
        }
        String impCur = request.imp().get(0).bidfloorcur();
        return isBlank(impCur) ? defaultCurrency : impCur.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CandidateOutcome(List<AdDocument> candidates, NoBidReason reason) {
        static CandidateOutcome ok(List<AdDocument> candidates) {
            return new CandidateOutcome(candidates, NoBidReason.NONE);
        }

        static CandidateOutcome fail(NoBidReason reason) {
            return new CandidateOutcome(List.of(), reason);
        }
    }

    private record ProfileOutcome(UserProfile profile, NoBidReason reason) {
        static ProfileOutcome ok(UserProfile profile) {
            return new ProfileOutcome(profile, NoBidReason.NONE);
        }

        static ProfileOutcome fail(NoBidReason reason) {
            return new ProfileOutcome(null, reason);
        }
    }
}
