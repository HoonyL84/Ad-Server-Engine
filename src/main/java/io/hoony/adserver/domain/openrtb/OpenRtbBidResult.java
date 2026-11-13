package io.hoony.adserver.domain.openrtb;

import io.hoony.adserver.domain.openrtb.dto.BidResponse;

/**
 * OpenRTB 입찰 결과 래퍼. 입찰 성공 시 BidResponse를, 미입찰 시 NoBidReason을 반환한다.
 */
public record OpenRtbBidResult(
        BidResponse bidResponse,
        NoBidReason noBidReason
) {
    public static OpenRtbBidResult bid(BidResponse bidResponse) {
        return new OpenRtbBidResult(bidResponse, NoBidReason.NONE);
    }

    public static OpenRtbBidResult noBid(NoBidReason reason) {
        return new OpenRtbBidResult(null, reason == null ? NoBidReason.NO_CANDIDATE : reason);
    }

    public boolean hasBid() {
        return bidResponse != null
                && bidResponse.seatbid() != null
                && !bidResponse.seatbid().isEmpty();
    }
}
