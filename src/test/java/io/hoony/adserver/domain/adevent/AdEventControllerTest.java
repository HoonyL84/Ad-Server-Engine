package io.hoony.adserver.domain.adevent;

import io.hoony.adserver.domain.ad.AdRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdEventController.class)
class AdEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdEventService adEventService;

    @MockBean
    private AdRepository adRepository;

    @Test
    @DisplayName("impression tracking URL 호출을 수집한다.")
    void collectsImpressionByUrl() throws Exception {
        when(adEventService.collect(any(), any()))
                .thenReturn(new AdEventResult("event-1", AdEventType.IMPRESSION, false));

        mockMvc.perform(get("/api/v1/ad-events/impressions")
                        .param("eventId", "event-1")
                        .param("requestId", "request-1")
                        .param("adId", "101")
                        .param("userId", "user-1")
                        .param("slotId", "home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("event-1"))
                .andExpect(jsonPath("$.eventType").value("IMPRESSION"))
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    @DisplayName("click tracking URL 호출을 수집한다.")
    void collectsClickByUrl() throws Exception {
        when(adEventService.collect(any(), any()))
                .thenReturn(new AdEventResult("event-2", AdEventType.CLICK, true));

        mockMvc.perform(get("/api/v1/ad-events/clicks")
                        .param("eventId", "event-2")
                        .param("requestId", "request-1")
                        .param("adId", "101")
                        .param("userId", "user-1")
                        .param("slotId", "home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("event-2"))
                .andExpect(jsonPath("$.eventType").value("CLICK"))
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    @DisplayName("click tracking URL에 landingUrl이 있으면 이벤트 수집 후 redirect 한다.")
    void redirectsClickWhenLandingUrlExists() throws Exception {
        io.hoony.adserver.domain.ad.Ad mockAd = mock(io.hoony.adserver.domain.ad.Ad.class);
        when(mockAd.getClickUrl()).thenReturn("https://advertiser.example/landing");
        when(adRepository.findById(101L)).thenReturn(java.util.Optional.of(mockAd));

        when(adEventService.collect(any(), any()))
                .thenReturn(new AdEventResult("event-3", AdEventType.CLICK, false));

        mockMvc.perform(get("/api/v1/ad-events/clicks")
                        .param("eventId", "event-3")
                        .param("requestId", "request-1")
                        .param("adId", "101")
                        .param("userId", "user-1")
                        .param("slotId", "home")
                        .param("landingUrl", "https://advertiser.example/landing"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://advertiser.example/landing"));
    }

    @Test
    @DisplayName("존재하지 않는 adId로 클릭 추적을 요청하면 400 Bad Request를 응답한다.")
    void returnsBadRequestForInvalidAdIdOnClicks() throws Exception {
        when(adRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/ad-events/clicks")
                        .param("eventId", "event-4")
                        .param("requestId", "request-1")
                        .param("adId", "999")
                        .param("userId", "user-1")
                        .param("slotId", "home")
                        .param("landingUrl", "https://advertiser.example/landing"))
                .andExpect(status().isBadRequest());
    }
}
