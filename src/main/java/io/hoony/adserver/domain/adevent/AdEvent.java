package io.hoony.adserver.domain.adevent;

import io.hoony.adserver.domain.support.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "ad_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_ad_event_event_id", columnNames = "event_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "ad_id", nullable = false)
    private Long adId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "slot_id", nullable = false, length = 50)
    private String slotId;

    @Column(name = "landing_url", length = 1000)
    private String landingUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private AdEventType eventType;

    private AdEvent(
            String eventId,
            String requestId,
            Long adId,
            String userId,
            String slotId,
            String landingUrl,
            AdEventType eventType
    ) {
        this.eventId = eventId;
        this.requestId = requestId;
        this.adId = adId;
        this.userId = userId;
        this.slotId = slotId;
        this.landingUrl = landingUrl;
        this.eventType = eventType;
    }

    public static AdEvent of(AdEventType eventType, AdEventRequest request) {
        return new AdEvent(
                request.eventId(),
                request.requestId(),
                request.adId(),
                request.userId(),
                request.slotId(),
                request.landingUrl(),
                eventType
        );
    }
}
