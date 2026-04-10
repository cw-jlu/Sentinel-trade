package com.sentinel_trade.controller;

import com.sentinel_trade.model.KLineEntity;
import com.sentinel_trade.repository.ClickHouseRepository;
import com.sentinel_trade.repository.KLineRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * REST controller for historical data queries.
 * Requirements: 5.3, 5.4
 */
@RestController
@RequestMapping("/api/history")
@Validated
@Tag(name = "History", description = "Historical tick and K-line data queries")
public class HistoryQueryController {

    private static final Logger LOG = LoggerFactory.getLogger(HistoryQueryController.class);
    private static final int DEFAULT_PAGE_SIZE = 500;

    private final ClickHouseRepository clickHouseRepository;
    private final KLineRepository kLineRepository;

    public HistoryQueryController(ClickHouseRepository clickHouseRepository,
                                   KLineRepository kLineRepository) {
        this.clickHouseRepository = clickHouseRepository;
        this.kLineRepository = kLineRepository;
    }

    /**
     * Query raw tick data from ClickHouse.
     * Requirements: 5.3
     */
    @GetMapping("/ticks")
    @Operation(summary = "Query historical tick data")
    public ResponseEntity<List<Map<String, Object>>> queryTicks(
            @RequestParam @NotBlank String symbol,
            @RequestParam @Positive long startTime,
            @RequestParam @Positive long endTime) {

        LOG.info("Tick query: symbol={} startTime={} endTime={}", symbol, startTime, endTime);
        List<Map<String, Object>> result = clickHouseRepository.queryTicks(symbol, startTime, endTime);
        return ResponseEntity.ok(result);
    }

    /**
     * Query aggregated K-line data from MySQL.
     * Requirements: 5.3, 5.4
     */
    @GetMapping("/klines")
    @Operation(summary = "Query historical K-line data")
    public ResponseEntity<List<KLineEntity>> queryKLines(
            @RequestParam @NotBlank String symbol,
            @RequestParam @NotBlank String interval,
            @RequestParam @Positive long startTime,
            @RequestParam @Positive long endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        LOG.info("KLine query: symbol={} interval={} startTime={} endTime={}",
                symbol, interval, startTime, endTime);

        LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneOffset.UTC);
        LocalDateTime end   = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime),   ZoneOffset.UTC);

        List<KLineEntity> result = kLineRepository.findBySymbolAndIntervalAndOpenTimeBetween(
                symbol, interval, start, end,
                PageRequest.of(page, Math.min(size, 5000))).getContent();

        return ResponseEntity.ok(result);
    }
}
