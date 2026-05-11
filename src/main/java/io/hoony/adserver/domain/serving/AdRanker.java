package io.hoony.adserver.domain.serving;

import io.hoony.adserver.domain.ad.search.AdDocument;

import java.util.List;
import java.util.Optional;

public interface AdRanker {

    List<AdDocument> rank(List<AdDocument> candidates);

    Optional<AdDocument> select(List<AdDocument> candidates);
}
