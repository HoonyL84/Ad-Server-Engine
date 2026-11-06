package io.hoony.adserver.domain.serving;

import java.util.List;

/**
 * 유저의 모호한 검색어를 광고 타게팅용 연관 카테고리 태그로 확장하는 LLM 어댑터 접점.
 */
@FunctionalInterface
public interface LlmQueryExpander {

    List<String> expandQueryToTags(String normalizedQuery);
}
