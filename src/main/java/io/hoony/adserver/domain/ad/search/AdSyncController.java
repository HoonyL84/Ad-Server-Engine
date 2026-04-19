package io.hoony.adserver.domain.ad.search;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [Senior Insight] Admin Sync Controller
 * 시스템 초기화 및 데이터 정합성 수동 확인을 위한 관리자 API입니다.
 * 실무에서는 접근 제어(Spring Security 등)가 필요하지만, 현재는 엔진 구현에 집중합니다.
 */
@RestController
@RequestMapping("/api/v1/ad/sync")
@RequiredArgsConstructor
public class AdSyncController {

    private final AdSyncService adSyncService;

    /**
     * 전체 광고 데이터를 MySQL -> ES로 수동 동기화 트리거
     */
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
