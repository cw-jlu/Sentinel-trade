package com.sentinel_trade.repository;

import com.sentinel_trade.model.KLineEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for KLine cold storage (MySQL).
 * Requirements: 5.3
 */
@Repository
public interface KLineRepository extends JpaRepository<KLineEntity, Long> {

    List<KLineEntity> findBySymbolAndIntervalAndOpenTimeBetween(
            String symbol, String interval,
            LocalDateTime startTime, LocalDateTime endTime);

    Page<KLineEntity> findBySymbolAndIntervalAndOpenTimeBetween(
            String symbol, String interval,
            LocalDateTime startTime, LocalDateTime endTime,
            Pageable pageable);
}
