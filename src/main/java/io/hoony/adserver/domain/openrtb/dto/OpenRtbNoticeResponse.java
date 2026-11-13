package io.hoony.adserver.domain.openrtb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * OpenRTB Win(nurl)/Loss(lurl) 알림 수신 응답 DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenRtbNoticeResponse(
        String bidId,
        String requestId,
        String noticeType,
        Long adId,
        String slotId,
        BigDecimal price,
        String reason
) {
}
