package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.support.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ad_search_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdSearchOutbox extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ad_id", nullable = false)
    private Long adId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private AdSearchOutboxEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdSearchOutboxStatus status;

    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public static AdSearchOutbox pending(
            Long adId,
            AdSearchOutboxEventType eventType,
            String payloadJson,
            String lastError
    ) {
        AdSearchOutbox outbox = new AdSearchOutbox();
        outbox.adId = adId;
        outbox.eventType = eventType;
        outbox.status = AdSearchOutboxStatus.PENDING;
        outbox.payloadJson = payloadJson;
        outbox.attemptCount = 0;
        outbox.nextRetryAt = LocalDateTime.now();
        outbox.lastError = truncate(lastError);
        return outbox;
    }

    public void markSucceeded() {
        this.status = AdSearchOutboxStatus.SUCCEEDED;
        this.lastError = null;
    }

    public void markRetry(String error, LocalDateTime nextRetryAt) {
        this.status = AdSearchOutboxStatus.PENDING;
        this.attemptCount++;
        this.nextRetryAt = nextRetryAt;
        this.lastError = truncate(error);
    }

    public void markFailed(String error) {
        this.status = AdSearchOutboxStatus.FAILED;
        this.attemptCount++;
        this.lastError = truncate(error);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
