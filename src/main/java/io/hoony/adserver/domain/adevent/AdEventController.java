package io.hoony.adserver.domain.adevent;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ad-events")
public class AdEventController {

    private final AdEventService adEventService;

    @PostMapping(value = "/impressions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdEventResponse> collectImpression(@Valid @RequestBody AdEventRequest request) {
        return ResponseEntity.ok(toResponse(adEventService.collect(AdEventType.IMPRESSION, request)));
    }

    @GetMapping("/impressions")
    public ResponseEntity<AdEventResponse> collectImpressionByUrl(
            @RequestParam String eventId,
            @RequestParam String requestId,
            @RequestParam Long adId,
            @RequestParam String userId,
            @RequestParam String slotId
    ) {
        return ResponseEntity.ok(toResponse(adEventService.collect(
                AdEventType.IMPRESSION,
                new AdEventRequest(eventId, requestId, adId, userId, slotId, null)
        )));
    }

    @PostMapping(value = "/clicks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AdEventResponse> collectClick(@Valid @RequestBody AdEventRequest request) {
        return ResponseEntity.ok(toResponse(adEventService.collect(AdEventType.CLICK, request)));
    }

    @GetMapping("/clicks")
    public Object collectClickByUrl(
            @RequestParam String eventId,
            @RequestParam String requestId,
            @RequestParam Long adId,
            @RequestParam String userId,
            @RequestParam String slotId,
            @RequestParam(required = false) String landingUrl
    ) {
        AdEventResult result = adEventService.collect(
                AdEventType.CLICK,
                new AdEventRequest(eventId, requestId, adId, userId, slotId, landingUrl)
        );

        if (landingUrl == null || landingUrl.isBlank()) {
            return ResponseEntity.ok(toResponse(result));
        }

        return new RedirectView(landingUrl);
    }

    private AdEventResponse toResponse(AdEventResult result) {
        return new AdEventResponse(result.eventId(), result.eventType().name(), result.duplicate());
    }
}
