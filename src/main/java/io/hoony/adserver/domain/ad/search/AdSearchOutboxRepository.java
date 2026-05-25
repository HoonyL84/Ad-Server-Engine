package io.hoony.adserver.domain.ad.search;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AdSearchOutboxRepository extends JpaRepository<AdSearchOutbox, Long> {

    List<AdSearchOutbox> findTop50ByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(
            AdSearchOutboxStatus status,
            LocalDateTime now
    );
}
