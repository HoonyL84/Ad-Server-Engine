package io.hoony.adserver.domain.adevent;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * [모듈 06] 클릭 후 7일 전환(Conversion) 기여 분석 (Last-Click Attribution) 도메인 서비스.
 * - 유저가 구매 전환을 일으켰을 때, 직전 7일(Lookback Window) 이내에 발생한 동일 유저의 클릭 중
 *   가장 마지막에 발생한 클릭(Last-Click)에 전환 성과를 귀속시키고 ROAS(%)를 산출한다.
 */
@Service
public class AdAttributionService {

    public static final Duration DEFAULT_LOOKBACK_WINDOW = Duration.ofDays(7);

    public Optional<AttributionResult> attributeLastClick(
            List<ClickTouchpoint> clickTouchpoints,
            ConversionEvent conversionEvent
    ) {
        return attributeLastClick(clickTouchpoints, conversionEvent, DEFAULT_LOOKBACK_WINDOW);
    }

    public Optional<AttributionResult> attributeLastClick(
            List<ClickTouchpoint> clickTouchpoints,
            ConversionEvent conversionEvent,
            Duration lookbackWindow
    ) {
        if (clickTouchpoints == null || clickTouchpoints.isEmpty() || conversionEvent == null) {
            return Optional.empty();
        }
        if (conversionEvent.userId() == null || conversionEvent.convertedAt() == null) {
            return Optional.empty();
        }

        Duration window = (lookbackWindow == null || lookbackWindow.isNegative())
                ? DEFAULT_LOOKBACK_WINDOW
                : lookbackWindow;
        Instant windowStart = conversionEvent.convertedAt().minus(window);

        // 1단계: 동일 유저의 클릭 중 룩백 윈도우(구매 전 7일 ~ 구매 시각) 내 유효 클릭 탐색
        ClickTouchpoint winningClick = null;
        for (ClickTouchpoint click : clickTouchpoints) {
            if (click == null || click.clickedAt() == null || click.userId() == null) {
                continue;
            }
            if (!conversionEvent.userId().equals(click.userId())) {
                continue;
            }
            if (click.clickedAt().isBefore(windowStart) || click.clickedAt().isAfter(conversionEvent.convertedAt())) {
                continue;
            }

            // 2단계: 가장 마지막에 발생한 클릭(Last-Click) 선택
            if (winningClick == null || click.clickedAt().isAfter(winningClick.clickedAt())) {
                winningClick = click;
            }
        }

        if (winningClick == null) {
            return Optional.empty();
        }

        // 3단계: 전환 기여 확정 및 ROAS(%) 산출
        double roasPercent = calculateRoas(conversionEvent.conversionAmount(), winningClick.adCost());
        return Optional.of(new AttributionResult(
                winningClick.adId(),
                winningClick.clickEventId(),
                conversionEvent.orderId(),
                conversionEvent.conversionAmount(),
                winningClick.adCost(),
                roasPercent
        ));
    }

    /**
     * ROAS(Return On Ad Spend, %) = (전환 매출액 / 광고 집행 비용) * 100
     */
    public double calculateRoas(long conversionAmount, long adCost) {
        if (conversionAmount <= 0L || adCost <= 0L) {
            return 0.0;
        }
        return ((double) conversionAmount / adCost) * 100.0;
    }

    public record ClickTouchpoint(
            String clickEventId,
            Long adId,
            String userId,
            Instant clickedAt,
            long adCost
    ) {
    }

    public record ConversionEvent(
            String orderId,
            String userId,
            Instant convertedAt,
            long conversionAmount
    ) {
    }

    public record AttributionResult(
            Long attributedAdId,
            String clickEventId,
            String orderId,
            long conversionAmount,
            long adCost,
            double roasPercent
    ) {
    }
}
