package io.hoony.adserver.domain.adevent;

import io.hoony.adserver.domain.adstatistic.AdEventCountDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AdEventRepository extends JpaRepository<AdEvent, Long> {

    boolean existsByEventId(String eventId);

    long countByAdIdAndEventType(Long adId, AdEventType eventType);

    @Query("SELECT new io.hoony.adserver.domain.adstatistic.AdEventCountDto(e.adId, e.eventType, COUNT(e)) " +
           "FROM AdEvent e GROUP BY e.adId, e.eventType")
    List<AdEventCountDto> countAllEventsGrouped();

    @Query("SELECT new io.hoony.adserver.domain.adstatistic.AdEventCountDto(e.adId, e.eventType, COUNT(e)) " +
           "FROM AdEvent e " +
           "WHERE e.adId IN :adIds " +
           "GROUP BY e.adId, e.eventType")
    List<AdEventCountDto> countEventsGroupedByAdIds(@Param("adIds") Collection<Long> adIds);
}
