package io.hoony.adserver.domain.openrtb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * OpenRTB 2.x 개별 입찰(Bid) DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Bid(
        String id,
        String impid,
        BigDecimal price,
        String adid,
        String nurl,
        String lurl,
        String adm,
        String crid
) {
}
