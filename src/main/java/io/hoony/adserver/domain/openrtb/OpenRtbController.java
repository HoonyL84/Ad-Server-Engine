package io.hoony.adserver.domain.openrtb;

import io.hoony.adserver.domain.openrtb.dto.BidRequest;
import io.hoony.adserver.domain.openrtb.dto.BidResponse;
import io.hoony.adserver.domain.openrtb.dto.OpenRtbNoticeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * OpenRTB 2.x 입찰 및 Win(nurl)/Loss(lurl) 알림 엔드포인트.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/openrtb")
public class OpenRtbController {

    public static final String NO_BID_REASON_HEADER = "X-OpenRtb-NoBid-Reason";

    private final OpenRtbBidderService openRtbBidderService;
    private final OpenRtbMetrics openRtbMetrics;

    @PostMapping(
            value = "/bid",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<BidResponse> bid(@Valid @RequestBody BidRequest request) {
        OpenRtbBidResult result = openRtbBidderService.bid(request);
        if (!result.hasBid()) {
            return ResponseEntity.noContent()
                    .header(NO_BID_REASON_HEADER, result.noBidReason().name())
                    .build();
        }
        return ResponseEntity.ok(result.bidResponse());
    }

    @GetMapping("/win")
    public ResponseEntity<OpenRtbNoticeResponse> winNotice(
            @RequestParam String bidId,
            @RequestParam String requestId,
            @RequestParam(required = false) String impId,
            @RequestParam Long adId,
            @RequestParam String slotId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) BigDecimal price
    ) {
        openRtbMetrics.recordWin(slotId);
        log.info("OpenRTB win notice received. bidId={}, requestId={}, impId={}, adId={}, slotId={}, userId={}, price={}",
                bidId, requestId, impId, adId, slotId, userId, price);
        return ResponseEntity.ok(new OpenRtbNoticeResponse(
                bidId, requestId, "WIN", adId, slotId, price, null
        ));
    }

    @GetMapping("/loss")
    public ResponseEntity<OpenRtbNoticeResponse> lossNotice(
            @RequestParam String bidId,
            @RequestParam String requestId,
            @RequestParam(required = false) String impId,
            @RequestParam Long adId,
            @RequestParam String slotId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String reason
    ) {
        openRtbMetrics.recordLoss(slotId, reason);
        log.info("OpenRTB loss notice received. bidId={}, requestId={}, impId={}, adId={}, slotId={}, userId={}, reason={}",
                bidId, requestId, impId, adId, slotId, userId, reason);
        return ResponseEntity.ok(new OpenRtbNoticeResponse(
                bidId, requestId, "LOSS", adId, slotId, null, reason
        ));
    }
}
