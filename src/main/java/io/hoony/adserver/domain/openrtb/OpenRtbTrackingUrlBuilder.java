package io.hoony.adserver.domain.openrtb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hoony.adserver.domain.ad.search.AdDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OpenRTB 입찰 응답의 win(nurl)/loss(lurl)/impression/click 트래킹 URL 및 adm 마크업 생성기.
 * - impression/click URL은 기존 Step 12 이벤트 수집 엔드포인트(/api/v1/ad-events/*)와 동일한 계약을 재사용한다.
 * - win/loss URL은 OpenRTB 전용 알림 엔드포인트(/api/v1/openrtb/win, /loss)로 연결한다.
 */
@Component
public class OpenRtbTrackingUrlBuilder {

    private static final String ANONYMOUS_USER = "anonymous";

    private final ObjectMapper objectMapper;

    public OpenRtbTrackingUrlBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildWinUrl(String bidId, String requestId, String impId, Long adId, String slotId, String userId) {
        return baseUriBuilder()
                .path("/api/v1/openrtb/win")
                .queryParam("bidId", bidId)
                .queryParam("requestId", requestId)
                .queryParam("impId", impId)
                .queryParam("adId", adId)
                .queryParam("slotId", slotId)
                .queryParam("userId", safeUserId(userId))
                .toUriString() + "&price=${AUCTION_PRICE}";
    }

    public String buildLossUrl(String bidId, String requestId, String impId, Long adId, String slotId, String userId) {
        return baseUriBuilder()
                .path("/api/v1/openrtb/loss")
                .queryParam("bidId", bidId)
                .queryParam("requestId", requestId)
                .queryParam("impId", impId)
                .queryParam("adId", adId)
                .queryParam("slotId", slotId)
                .queryParam("userId", safeUserId(userId))
                .toUriString() + "&reason=${AUCTION_LOSS}";
    }

    public String buildImpressionUrl(String bidId, String requestId, Long adId, String userId, String slotId) {
        return eventUrl("impressions", bidId, requestId, adId, safeUserId(userId), slotId, null);
    }

    public String buildClickTrackingUrl(
            String bidId,
            String requestId,
            Long adId,
            String userId,
            String slotId,
            String landingUrl
    ) {
        return eventUrl("clicks", bidId, requestId, adId, safeUserId(userId), slotId, landingUrl);
    }

    public String buildAdm(
            AdDocument ad,
            String bidId,
            String requestId,
            String userId,
            String slotId
    ) {
        CreativeMarkup markup = new CreativeMarkup(
                ad.getId(),
                ad.getTitle(),
                ad.getImageUrl(),
                ad.getClickUrl(),
                buildImpressionUrl(bidId, requestId, ad.getId(), userId, slotId),
                buildClickTrackingUrl(bidId, requestId, ad.getId(), userId, slotId, ad.getClickUrl())
        );
        try {
            return objectMapper.writeValueAsString(markup);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OpenRTB creative markup", e);
        }
    }

    private String eventUrl(
            String eventPath,
            String bidId,
            String requestId,
            Long adId,
            String userId,
            String slotId,
            String landingUrl
    ) {
        String eventId = bidId + ":" + eventPath;
        UriComponentsBuilder builder = baseUriBuilder()
                .path("/api/v1/ad-events/")
                .path(eventPath)
                .queryParam("eventId", eventId)
                .queryParam("requestId", requestId)
                .queryParam("adId", adId)
                .queryParam("userId", userId)
                .queryParam("slotId", slotId);

        if (landingUrl != null && !landingUrl.isBlank()) {
            builder.queryParam("landingUrl", landingUrl);
        }
        return builder.toUriString();
    }

    private UriComponentsBuilder baseUriBuilder() {
        // 단위 테스트 등 서블릿 요청 컨텍스트가 없는 환경에서는 상대 경로로 안전하게 fallback한다.
        if (RequestContextHolder.getRequestAttributes() != null) {
            return ServletUriComponentsBuilder.fromCurrentContextPath();
        }
        return UriComponentsBuilder.newInstance();
    }

    private String safeUserId(String userId) {
        return (userId == null || userId.isBlank()) ? ANONYMOUS_USER : userId;
    }

    private record CreativeMarkup(
            Long adId,
            String title,
            String imageUrl,
            String clickUrl,
            String impressionUrl,
            String clickTrackingUrl
    ) {
    }
}
