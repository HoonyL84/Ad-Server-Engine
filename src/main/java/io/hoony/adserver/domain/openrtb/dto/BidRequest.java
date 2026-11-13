package io.hoony.adserver.domain.openrtb.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * OpenRTB 2.x 최소 입찰 요청(BidRequest) DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BidRequest(
        @NotBlank(message = "BidRequest id must not be blank")
        String id,
        @NotEmpty(message = "BidRequest imp must not be empty")
        @Valid
        List<Imp> imp,
        Site site,
        App app,
        User user,
        @Min(value = 1, message = "tmax must be positive")
        Integer tmax,
        List<String> cur
) {
    public BidRequest(String id, List<Imp> imp, User user, Integer tmax) {
        this(id, imp, null, null, user, tmax, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Site(String id, String domain) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record App(String id, String bundle) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record User(String id, String gender) {
        public User(String id) {
            this(id, null);
        }
    }
}
