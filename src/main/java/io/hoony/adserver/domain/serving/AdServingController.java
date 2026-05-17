package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ads")
public class AdServingController {

    private final AdServingService adServingService;

    @GetMapping("/serve")
    public ResponseEntity<AdServingResponse> serve(@Valid AdServingRequest request) {
        AdServingResult result = adServingService.serve(request.userId(), request.slotId());
        AdDocument selected = result.selectedAd();
        String requestId = UUID.randomUUID().toString();

        AdServingResponse response = new AdServingResponse(
                requestId,
                selected == null ? null : selected.getId(),
                selected == null ? null : selected.getTitle(),
                selected == null ? null : selected.getImageUrl(),
                selected == null ? null : selected.getClickUrl(),
                selected == null ? null : eventUrl("impressions", requestId, selected.getId(), request, null),
                selected == null ? null : eventUrl("clicks", requestId, selected.getId(), request, selected.getClickUrl()),
                result.fallback(),
                result.fallbackReason().name()
        );
        return ResponseEntity.ok(response);
    }

    private String eventUrl(String eventPath, String requestId, Long adId, AdServingRequest request, String landingUrl) {
        String eventId = requestId + ":" + eventPath;

        UriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/ad-events/")
                .path(eventPath)
                .queryParam("eventId", eventId)
                .queryParam("requestId", requestId)
                .queryParam("adId", adId)
                .queryParam("userId", request.userId())
                .queryParam("slotId", request.slotId());

        if (landingUrl != null && !landingUrl.isBlank()) {
            builder.queryParam("landingUrl", landingUrl);
        }

        return builder.toUriString();
    }
}
