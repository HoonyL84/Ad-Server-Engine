package io.hoony.adserver.domain.adstatistic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdStatisticRepository extends JpaRepository<AdStatistic, Long> {
}
