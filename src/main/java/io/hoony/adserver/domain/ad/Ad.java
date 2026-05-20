package io.hoony.adserver.domain.ad;

import io.hoony.adserver.domain.advertiser.Advertiser;
import io.hoony.adserver.domain.support.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "ad")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ad extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "click_url", nullable = false)
    private String clickUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advertiser_id", nullable = false)
    private Advertiser advertiser;

    @Column(name = "max_bid", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxBid;

    @Column(name = "total_budget", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalBudget;

    @Column(name = "spent_amount", precision = 19, scale = 2)
    private BigDecimal spentAmount = BigDecimal.ZERO;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AdStatus status = AdStatus.ACTIVE;

    @Convert(converter = TargetContextConverter.class)
    @Column(name = "target_context", columnDefinition = "TEXT")
    private Map<String, Object> targetContext = new HashMap<>();

    @Column(name = "target_gender")
    private String targetGender;

    @Column(name = "target_location_id")
    private String targetLocationId;

    @Column(name = "target_interest_tags")
    private String targetInterestTags;

    @Builder
    public Ad(String title, String imageUrl, String clickUrl, Advertiser advertiser, BigDecimal maxBid, BigDecimal totalBudget,
              BigDecimal spentAmount, LocalDateTime startDate, LocalDateTime endDate, AdStatus status,
              Map<String, Object> targetContext, String targetGender, String targetLocationId, String targetInterestTags) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.clickUrl = clickUrl;
        this.advertiser = advertiser;
        this.maxBid = maxBid;
        this.totalBudget = totalBudget;
        this.spentAmount = spentAmount != null ? spentAmount : BigDecimal.ZERO;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status != null ? status : AdStatus.ACTIVE;
        this.targetContext = targetContext != null ? targetContext : new HashMap<>();
        this.targetGender = targetGender;
        this.targetLocationId = targetLocationId;
        this.targetInterestTags = targetInterestTags;
    }

    public void updateTargetContext(Map<String, Object> newContext) {
        this.targetContext = newContext;
    }

    public void updateTotalBudget(BigDecimal newBudget) {
        if (newBudget == null || newBudget.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Budget cannot be negative or null");
        }
        this.totalBudget = newBudget;
    }
}
