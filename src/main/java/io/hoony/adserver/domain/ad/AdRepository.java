package io.hoony.adserver.domain.ad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AdRepository extends JpaRepository<Ad, Long> {

    @Query("select a from Ad a join fetch a.advertiser where a.id = :id")
    Ad findByIdWithAdvertiser(@Param("id") Long id);

    @Query("select a from Ad a join fetch a.advertiser")
    List<Ad> findAllWithAdvertiser();
}
