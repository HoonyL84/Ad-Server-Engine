package io.hoony.adserver.domain.adevent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
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
}
