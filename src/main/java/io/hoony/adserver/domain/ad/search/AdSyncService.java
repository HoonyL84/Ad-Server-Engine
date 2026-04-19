package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.Ad;
import io.hoony.adserver.domain.ad.AdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * [Senior Insight] Bulk Sync Service (Chunk-based)
 * MySQL의 데이터를 Elasticsearch로 분할 동기화하는 서비스입니다.
 * findAll() 대신 Paging을 사용하여 수백만 건의 데이터도 OOM 없이 처리 가능합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdSyncService {

    private final AdRepository adRepository;
    private final AdSearchRepository adSearchRepository;

    private static final int CHUNK_SIZE = 100;

    /**
     * MySQL의 모든 광고 데이터를 Chunk 단위로 조회하여 Elasticsearch로 일괄 인덱싱합니다.
     */
    @Transactional(readOnly = true)
    public long syncAllAds() {
        log.info("Starting Chunk-based Bulk Synchronization: MySQL -> Elasticsearch");

        long totalSyncedCount = 0;
        int page = 0;
        Page<Ad> adPage;

        do {
            // 1. Chunk 단위(100건)로 데이터 페이징 조회
            adPage = adRepository.findAll(PageRequest.of(page, CHUNK_SIZE));
            List<Ad> ads = adPage.getContent();

            if (ads.isEmpty()) break;

            log.info("Processing Chunk [Page: {}, Size: {}]", page, ads.size());

            // 2. 검색 전용 도큐먼트(AdDocument)로 변환
            List<AdDocument> documents = ads.stream()
                    .map(this::convertToDocument)
                    .collect(Collectors.toList());

            // 3. Bulk Indexing 수행 (해당 Chunk 저장)
            adSearchRepository.saveAll(documents);

            totalSyncedCount += documents.size();
            page++;

        } while (adPage.hasNext());

        log.info("Successfully synced a total of {} ads to Elasticsearch in {} chunks", totalSyncedCount, page);

        return totalSyncedCount;
    }

    /**
     * [Consistency] AdSearchEventListener와 동일한 변환 로직을 사용합니다.
     */
    private AdDocument convertToDocument(Ad ad) {
        return AdDocument.builder()
                .id(ad.getId())
                .advertiserId(ad.getAdvertiser().getId())
                .title(ad.getTitle())
                .maxBid(ad.getMaxBid())
                .status(ad.getStatus())
                .targetGender(ad.getTargetGender())
                .targetLocationId(ad.getTargetLocationId())
                .interestTags(parseTags(ad.getTargetInterestTags()))
                .targetContext(ad.getTargetContext())
                .build();
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }
}
