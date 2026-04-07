package io.hoony.adserver.domain.advertiser;

import io.hoony.adserver.domain.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "advertiser")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Advertiser extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdvertiserStatus status;

    @Builder
    public Advertiser(String name, AdvertiserStatus status) {
        this.name = name;
        this.status = status;
    }

    public void updateStatus(AdvertiserStatus status) {
        this.status = status;
    }
}
