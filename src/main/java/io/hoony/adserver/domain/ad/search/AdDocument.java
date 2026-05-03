package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.AdStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * [Senior Insight] 왜 ES 전용 AdDocument를 별도로 정의하는가?
 * 1. 계층 간 격리: JPA Entity(RDB)와 ES Document(NoSQL)는 데이터 모델이 다릅니다. 하나에 통합하면 어노테이션 지옥이 됩니다.
 * 2. 타입 최적화: RDB는 콤마(,)로 된 문자열이지만, ES는 배열(List)로 저장해야 고속 '태그 검색'이 가능합니다.
 * 3. 검색 전용: 광고 서빙 시 불필요한 필드(이미지/클릭 URL 등)는 제외하고 검색에 꼭 필요한 데이터만 인덱싱하여 성능을 극대화합니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Document(indexName = "ads") // Elasticsearch의 'ads' 인덱스와 매핑됨을 명시
@Setting(shards = 1, replicas = 0) // 테스트 환경이므로 샤드 1개, 복제본 0개로 설정 (운영 시 조정 필요)
public class AdDocument {

    @Id // ES의 _id 필드와 매핑
    private Long id;

    /**
     * [Field Analysis] - Keyword vs Text
     * - Keyword: 문자열을 쪼개지 않고 '완전 일치'로만 검색합니다. (성별, 지역, 상태 등)
     * - Text: 문자열을 형태소 분석기로 쪼개서 '부분 검색'이 가능하게 합니다. (광고 제목 등)
     */
    @Field(type = FieldType.Keyword)
    private Long advertiserId;

    @Field(type = FieldType.Text, analyzer = "standard") // 광고 제목은 키워드 기반 검색을 위해 분석 가능하게 설정
    private String title;

    @Field(type = FieldType.Keyword)
    private String imageUrl;

    @Field(type = FieldType.Keyword)
    private String clickUrl;

    @Field(type = FieldType.Double)
    private BigDecimal maxBid; // 랭킹 정렬을 위한 입찰가

    @Field(type = FieldType.Keyword)
    private AdStatus status; // ACTIVE 인 광고만 필터링하기 위함

    @Field(type = FieldType.Keyword)
    private String targetGender; // M, F, ALL - 하드 필터링용

    @Field(type = FieldType.Keyword)
    private String targetLocationId; // '1:11' 같은 계층형 지역 ID - 하드 필터링용

    /**
     * [Optimization] RDB에서는 '패션,신발' 이라는 문자열이지만,
     * ES에서는 List<String>으로 정의하여 인덱싱하면 내부적으로 배열로 저장됩니다.
     * 이를 통해 특정 태그를 포함하는지 여부를 인덱스 수준에서 초고속으로 판단할 수 있습니다.
     */
    @Field(type = FieldType.Keyword)
    private List<String> interestTags;

    /**
     * [Soft Filter] JSON 형태의 추가 타겟팅 문맥 (나이 등)
     * 이 필드는 ES에서 직접 검색하기보다, 추출된 후보군을 애플리케이션 레이어에서 
     * Strategy 패턴(AdMatcher)으로 정밀 검증할 때 사용합니다.
     */
    @Field(type = FieldType.Object)
    private Map<String, Object> targetContext;
}
