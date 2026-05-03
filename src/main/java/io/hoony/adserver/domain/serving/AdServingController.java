package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ads")
public class AdServingController {

    private final AdServingService adServingService;

    @GetMapping("/serve")
    public ResponseEntity<AdServingResponse> serve(@Valid AdServingRequest request) {
        AdServingResult result = adServingService.serve(request.userId(), request.slotId());
        AdDocument selected = result.selectedAd();

        AdServingResponse response = new AdServingResponse(
                selected == null ? null : selected.getId(),
                selected == null ? null : selected.getTitle(),
                selected == null ? null : selected.getImageUrl(),
                selected == null ? null : selected.getClickUrl(),
                result.fallback(),
                result.fallbackReason().name()
        );
        return ResponseEntity.ok(response);
    }
}
