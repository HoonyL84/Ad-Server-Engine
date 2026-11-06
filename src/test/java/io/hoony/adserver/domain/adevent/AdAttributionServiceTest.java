package io.hoony.adserver.domain.adevent;

import io.hoony.adserver.domain.adevent.AdAttributionService.AttributionResult;
import io.hoony.adserver.domain.adevent.AdAttributionService.ClickTouchpoint;
import io.hoony.adserver.domain.adevent.AdAttributionService.ConversionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AdAttributionServiceTest {

    private final AdAttributionService attributionService = new AdAttributionService();

    @Test
    @DisplayName("[0.1초 순수 POJO] 구매 전환 전 7일 이내 복수 클릭 중 가장 마지막 클릭(Last-Click)에 기여를 부여하고 ROAS를 산출한다")
    void attributesConversionToLastClickWithinSevenDays() {
        Instant convertedAt = Instant.parse("2026-11-07T12:00:00Z");

        ClickTouchpoint eightDaysAgoExpired = new ClickTouchpoint(
                "clk-expired", 10L, "user-1", convertedAt.minus(8, ChronoUnit.DAYS), 500L
        );
        ClickTouchpoint threeDaysAgo = new ClickTouchpoint(
                "clk-first", 20L, "user-1", convertedAt.minus(3, ChronoUnit.DAYS), 500L
        );
        ClickTouchpoint twoHoursAgoLastClick = new ClickTouchpoint(
                "clk-last", 30L, "user-1", convertedAt.minus(2, ChronoUnit.HOURS), 1000L
        );
        ClickTouchpoint otherUserClick = new ClickTouchpoint(
                "clk-other-user", 40L, "user-2", convertedAt.minus(1, ChronoUnit.HOURS), 1000L
        );

        ConversionEvent conversion = new ConversionEvent("order-100", "user-1", convertedAt, 50000L);

        Optional<AttributionResult> result = attributionService.attributeLastClick(
                List.of(eightDaysAgoExpired, threeDaysAgo, twoHoursAgoLastClick, otherUserClick),
                conversion
        );

        assertThat(result).isPresent();
        assertThat(result.get().attributedAdId()).isEqualTo(30L);
        assertThat(result.get().clickEventId()).isEqualTo("clk-last");
        assertThat(result.get().roasPercent()).isCloseTo(5000.0, within(1e-4)); // (50,000 / 1,000) * 100 = 5000%
    }

    @Test
    @DisplayName("7일 룩백 윈도우가 지났거나 구매 시각 이후에 발생한 클릭은 전환 기여에서 제외한다")
    void ignoresClicksOutsideLookbackWindowOrAfterConversion() {
        Instant convertedAt = Instant.parse("2026-11-07T12:00:00Z");

        ClickTouchpoint expiredClick = new ClickTouchpoint(
                "clk-old", 10L, "user-1", convertedAt.minus(7, ChronoUnit.DAYS).minusSeconds(1), 500L
        );
        ClickTouchpoint postConversionClick = new ClickTouchpoint(
                "clk-after", 20L, "user-1", convertedAt.plusSeconds(10), 500L
        );

        ConversionEvent conversion = new ConversionEvent("order-200", "user-1", convertedAt, 30000L);

        Optional<AttributionResult> result = attributionService.attributeLastClick(
                List.of(expiredClick, postConversionClick),
                conversion
        );

        assertThat(result).isEmpty();
    }
}
