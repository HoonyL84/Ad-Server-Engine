package io.hoony.adserver.domain.openrtb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * OpenRTB 2.x 최소 노출 지면(Imp) DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Imp(
        @NotBlank(message = "Imp id must not be blank")
        String id,
        String tagid,
        Banner banner,
        @DecimalMin(value = "0.0", message = "bidfloor must be >= 0")
        BigDecimal bidfloor,
        String bidfloorcur
) {
    public Imp(String id, String tagid, BigDecimal bidfloor) {
        this(id, tagid, null, bidfloor, "KRW");
    }

    public BigDecimal normalizedBidFloor() {
        return bidfloor == null ? BigDecimal.ZERO : bidfloor;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Banner(Integer w, Integer h) {
    }
}
