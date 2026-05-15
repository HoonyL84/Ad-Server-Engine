package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.AdStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Document(indexName = "ads")
@Setting(shards = 1, replicas = 0)
public class AdDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Keyword)
    private Long advertiserId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Keyword)
    private String imageUrl;

    @Field(type = FieldType.Keyword)
    private String clickUrl;

    @Field(type = FieldType.Double)
    private BigDecimal maxBid;

    @Field(type = FieldType.Double)
    private BigDecimal totalBudget;

    @Field(type = FieldType.Double)
    private BigDecimal spentAmount;

    @Field(type = FieldType.Keyword)
    private AdStatus status;

    @Field(type = FieldType.Keyword)
    private String targetGender;

    @Field(type = FieldType.Keyword)
    private String targetLocationId;

    @Field(type = FieldType.Keyword)
    private List<String> interestTags;

    @Field(type = FieldType.Object)
    private Map<String, Object> targetContext;

    public BigDecimal remainingBudget() {
        BigDecimal total = totalBudget == null ? BigDecimal.ZERO : totalBudget;
        BigDecimal spent = spentAmount == null ? BigDecimal.ZERO : spentAmount;
        return total.subtract(spent).max(BigDecimal.ZERO);
    }
}
