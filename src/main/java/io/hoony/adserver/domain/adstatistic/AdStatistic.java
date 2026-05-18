package io.hoony.adserver.domain.adstatistic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ad_statistic")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AdStatistic {

    @Id
    @Column(name = "ad_id")
    private Long adId;

    @Column(name = "impression_count", nullable = false)
    private long impressionCount;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    public static AdStatistic of(Long adId, long impressionCount, long clickCount) {
        return new AdStatistic(adId, impressionCount, clickCount);
    }
}
