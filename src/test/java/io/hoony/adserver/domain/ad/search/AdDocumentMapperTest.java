package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.AdStatus;
import io.hoony.adserver.domain.ad.event.AdEventPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdDocumentMapperTest {

    private final AdDocumentMapper mapper = new AdDocumentMapper();

    @Test
    @DisplayName("관심사와 지면을 서로 다른 ES 필드로 변환한다")
    void mapsInterestTagsAndSlotsSeparately() {
        AdDocument document = mapper.toDocument(payload("fashion,sports", "Home,LOCAL"));

        assertThat(document.getInterestTags()).containsExactly("fashion", "sports");
        assertThat(document.getSlotIds()).containsExactly("home", "local");
    }

    @Test
    @DisplayName("기존 광고의 빈 지면 값은 재색인 시 전체 지면으로 호환한다")
    void mapsLegacyMissingSlotsToAllSlots() {
        AdDocument document = mapper.toDocument(payload("finance", null));

        assertThat(document.getSlotIds()).containsExactly(AdDocument.ALL_SLOTS);
    }

    private AdEventPayload payload(String interestTags, String slotIds) {
        return new AdEventPayload(
                1L,
                10L,
                "ad",
                "image",
                "https://example.com",
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(100000),
                BigDecimal.ZERO,
                AdStatus.ACTIVE,
                "ALL",
                "0",
                interestTags,
                slotIds,
                Map.of(),
                Map.of()
        );
    }
}
