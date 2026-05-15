package io.hoony.adserver.domain.ad.search;

import io.hoony.adserver.domain.ad.Ad;
import io.hoony.adserver.domain.ad.AdRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdSyncService {

    private static final int CHUNK_SIZE = 100;

    private final AdRepository adRepository;
    private final AdSearchRepository adSearchRepository;
    private final AdDocumentMapper adDocumentMapper;

    @Transactional(readOnly = true)
    public long syncAllAds() {
        log.info("Starting Chunk-based Bulk Synchronization: MySQL -> Elasticsearch");

        long totalSyncedCount = 0;
        int page = 0;
        Page<Ad> adPage;

        do {
            adPage = adRepository.findAll(PageRequest.of(page, CHUNK_SIZE));
            List<Ad> ads = adPage.getContent();

            if (ads.isEmpty()) {
                break;
            }

            log.info("Processing Chunk [Page: {}, Size: {}]", page, ads.size());

            List<AdDocument> documents = ads.stream()
                    .map(adDocumentMapper::toDocument)
                    .toList();

            adSearchRepository.saveAll(documents);

            totalSyncedCount += documents.size();
            page++;
        } while (adPage.hasNext());

        log.info("Successfully synced a total of {} ads to Elasticsearch in {} chunks", totalSyncedCount, page);

        return totalSyncedCount;
    }
}
