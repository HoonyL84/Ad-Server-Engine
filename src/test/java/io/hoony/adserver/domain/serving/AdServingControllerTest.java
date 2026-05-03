package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.search.AdDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdServingController.class)
class AdServingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdServingService adServingService;

    @Test
    @DisplayName("서빙 성공 시 ad 정보와 fallback=false를 반환한다.")
    void returnsServingResult() throws Exception {
        AdDocument ad = AdDocument.builder()
                .id(101L)
                .advertiserId(1L)
                .title("test-ad")
                .imageUrl("https://cdn.ad-server.io/img/101.jpg")
                .clickUrl("https://ad-server.io/click/101")
                .maxBid(new BigDecimal("1000"))
                .status(AdStatus.ACTIVE)
                .targetGender("ALL")
                .targetLocationId("0")
                .interestTags(List.of("패션"))
                .build();

        when(adServingService.serve("1", "home"))
                .thenReturn(new AdServingResult(ad, false, ServingFallbackReason.NONE));

        mockMvc.perform(get("/api/v1/ads/serve")
                        .param("userId", "1")
                        .param("slotId", "home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adId").value(101))
                .andExpect(jsonPath("$.title").value("test-ad"))
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.ad-server.io/img/101.jpg"))
                .andExpect(jsonPath("$.clickUrl").value("https://ad-server.io/click/101"))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.fallbackReason").value("NONE"));
    }

    @Test
    @DisplayName("fallback 시 fallbackReason을 반환한다.")
    void returnsFallbackReason() throws Exception {
        when(adServingService.serve("slow-1", "home"))
                .thenReturn(new AdServingResult(null, true, ServingFallbackReason.DMP_TIMEOUT));

        mockMvc.perform(get("/api/v1/ads/serve")
                        .param("userId", "slow-1")
                        .param("slotId", "home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adId").isEmpty())
                .andExpect(jsonPath("$.imageUrl").isEmpty())
                .andExpect(jsonPath("$.clickUrl").isEmpty())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.fallbackReason").value("DMP_TIMEOUT"));
    }
}
