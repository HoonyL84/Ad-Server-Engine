package io.hoony.adserver.domain.ad.search;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ad/sync")
@RequiredArgsConstructor
public class AdSyncController {

    private final AdSyncService adSyncService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> syncAllAds() {
        long syncedCount = adSyncService.syncAllAds();

        return ResponseEntity.ok(Map.of(
                "message", "Bulk synchronization completed",
                "syncedCount", syncedCount,
                "status", "success"
        ));
    }
}
