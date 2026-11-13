package io.hoony.adserver.domain.openrtb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * OpenRTB 2.x 최소 입찰 응답(BidResponse) DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BidResponse(
        String id,
        List<SeatBid> seatbid,
        String bidid,
        String cur
) {
}
