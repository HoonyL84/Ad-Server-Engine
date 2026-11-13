package io.hoony.adserver.domain.openrtb;

import io.hoony.adserver.domain.openrtb.dto.Bid;
import io.hoony.adserver.domain.openrtb.dto.BidResponse;
import io.hoony.adserver.domain.openrtb.dto.SeatBid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OpenRtbController.class)
class OpenRtbControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenRtbBidderService openRtbBidderService;

    @MockitoBean
    private OpenRtbMetrics openRtbMetrics;

    @Test
    @DisplayName("POST /api/v1/openrtb/bid 입찰 성공 시 200 OK와 BidResponse JSON을 반환한다.")
    void returnsBidResponseOnSuccess() throws Exception {
        Bid bid = new Bid(
                "bid-1",
                "imp-1",
                new BigDecimal("1200"),
                "101",
                "http://localhost/api/v1/openrtb/win?bidId=bid-1&requestId=req-1&adId=101&slotId=home&price=${AUCTION_PRICE}",
                "http://localhost/api/v1/openrtb/loss?bidId=bid-1&requestId=req-1&adId=101&slotId=home&reason=${AUCTION_LOSS}",
                "{\"adId\":101}",
                "101"
        );
        BidResponse response = new BidResponse(
                "req-1",
                List.of(new SeatBid(List.of(bid), "ad-server-engine")),
                "bidres-1",
                "KRW"
        );
        when(openRtbBidderService.bid(any())).thenReturn(OpenRtbBidResult.bid(response));

        String payload = """
                {
                  "id": "req-1",
                  "imp": [
                    {
                      "id": "imp-1",
                      "tagid": "home",
                      "bidfloor": 500
                    }
                  ],
                  "user": { "id": "user-1" },
                  "tmax": 15
                }
                """;

        mockMvc.perform(post("/api/v1/openrtb/bid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("req-1"))
                .andExpect(jsonPath("$.cur").value("KRW"))
                .andExpect(jsonPath("$.seatbid[0].seat").value("ad-server-engine"))
                .andExpect(jsonPath("$.seatbid[0].bid[0].id").value("bid-1"))
                .andExpect(jsonPath("$.seatbid[0].bid[0].impid").value("imp-1"))
                .andExpect(jsonPath("$.seatbid[0].bid[0].price").value(1200))
                .andExpect(jsonPath("$.seatbid[0].bid[0].nurl").exists())
                .andExpect(jsonPath("$.seatbid[0].bid[0].lurl").exists())
                .andExpect(jsonPath("$.seatbid[0].bid[0].adm").exists());
    }

    @Test
    @DisplayName("POST /api/v1/openrtb/bid 미입찰 시 204 No Content와 사유 헤더를 반환한다.")
    void returnsNoContentOnNoBid() throws Exception {
        when(openRtbBidderService.bid(any())).thenReturn(OpenRtbBidResult.noBid(NoBidReason.BELOW_FLOOR));

        String payload = """
                {
                  "id": "req-2",
                  "imp": [
                    {
                      "id": "imp-1",
                      "tagid": "home",
                      "bidfloor": 9999
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/openrtb/bid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNoContent())
                .andExpect(header().string(OpenRtbController.NO_BID_REASON_HEADER, "BELOW_FLOOR"));
    }

    @Test
    @DisplayName("POST /api/v1/openrtb/bid 필수 필드 누락 또는 음수 bidfloor 요청 시 400 Bad Request를 반환한다.")
    void returnsBadRequestForInvalidBidRequest() throws Exception {
        String payload = """
                {
                  "id": "",
                  "imp": [
                    {
                      "id": "imp-1",
                      "tagid": "home",
                      "bidfloor": -10
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/openrtb/bid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/openrtb/win 알림 수신 시 메트릭을 기록하고 200 OK를 반환한다.")
    void recordsWinNotice() throws Exception {
        mockMvc.perform(get("/api/v1/openrtb/win")
                        .param("bidId", "bid-1")
                        .param("requestId", "req-1")
                        .param("impId", "imp-1")
                        .param("adId", "101")
                        .param("slotId", "home")
                        .param("price", "950"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bidId").value("bid-1"))
                .andExpect(jsonPath("$.noticeType").value("WIN"))
                .andExpect(jsonPath("$.adId").value(101))
                .andExpect(jsonPath("$.price").value(950));

        verify(openRtbMetrics).recordWin("home");
    }

    @Test
    @DisplayName("GET /api/v1/openrtb/loss 알림 수신 시 메트릭을 기록하고 200 OK를 반환한다.")
    void recordsLossNotice() throws Exception {
        mockMvc.perform(get("/api/v1/openrtb/loss")
                        .param("bidId", "bid-1")
                        .param("requestId", "req-1")
                        .param("impId", "imp-1")
                        .param("adId", "101")
                        .param("slotId", "home")
                        .param("reason", "102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bidId").value("bid-1"))
                .andExpect(jsonPath("$.noticeType").value("LOSS"))
                .andExpect(jsonPath("$.reason").value("102"));

        verify(openRtbMetrics).recordLoss("home", "102");
    }
}
