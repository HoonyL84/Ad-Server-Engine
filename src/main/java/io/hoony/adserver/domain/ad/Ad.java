package io.hoony.adserver.domain.ad;

import io.hoony.adserver.domain.advertiser.Advertiser;
import io.hoony.adserver.domain.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    private java.time.LocalDateTime startDate; // 광고 노출 시작 일시

    @Column(name = "end_date")
    private java.time.LocalDateTime endDate; // null일 경우 무제한

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AdStatus status = AdStatus.ACTIVE;

    /**
     * 유연한 타겟팅 정보 (JSON)
     */
    @Convert(converter = TargetContextConverter.class)
    @Column(name = "target_context", columnDefinition = "TEXT")
    private Map<String, Object> targetContext = new HashMap<>();

    /**
     * 타겟팅 필드: 성별 (M, F, ALL)
     */
    @Column(name = "target_gender")
    private String targetGender;

    /**
     * 타겟팅 필드: 계층형 지역 ID (예: '1:11' -> 서울:강남구)
     */
    @Column(name = "target_location_id")
    private String targetLocationId;

    /**
     * 고속 필터링용 필드 3: 관심사 태그
     * 콤마(,)로 구분된 태그 목록 (예: '운동,신발,패션')
     */
    @Column(name = "target_interest_tags")
    private String targetInterestTags;

    @Builder
    public Ad(String title, String imageUrl, String clickUrl, Advertiser advertiser, BigDecimal maxBid, BigDecimal totalBudget, 
              BigDecimal spentAmount, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, AdStatus status, 
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
}
