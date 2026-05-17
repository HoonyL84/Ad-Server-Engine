package io.hoony.adserver.domain.adevent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdEventRepository extends JpaRepository<AdEvent, Long> {

    boolean existsByEventId(String eventId);
}
