package io.hoony.adserver.domain.openrtb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * OpenRTB 2.x SeatBid DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SeatBid(
        List<Bid> bid,
        String seat
) {
}
