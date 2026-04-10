package com.sentinel_trade.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * JPA entity for kline_aggregated table (MySQL cold storage).
 * Requirements: 5.4
 */
@Entity
@Table(name = "kline_aggregated",
       indexes = @Index(name = "idx_symbol_interval_time",
                        columnList = "symbol, interval, open_time"))
public class KLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "`interval`", nullable = false, length = 4)
    private String interval;

    @Column(name = "open_time", nullable = false)
    @JsonIgnore
    private LocalDateTime openTime;

    @Column(name = "open_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 18, scale = 8)
    private BigDecimal closePrice;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal volume;

    @Column(name = "trade_count")
    private Integer tradeCount;

    // Getters / Setters
    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }
    public LocalDateTime getOpenTime() { return openTime; }
    
    @JsonProperty("openTime")
    public long getOpenTimeEpoch() {
        return openTime == null ? 0L : openTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public void setOpenTime(LocalDateTime openTime) { this.openTime = openTime; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public void setOpenPrice(BigDecimal openPrice) { this.openPrice = openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public void setHighPrice(BigDecimal highPrice) { this.highPrice = highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public void setLowPrice(BigDecimal lowPrice) { this.lowPrice = lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public void setClosePrice(BigDecimal closePrice) { this.closePrice = closePrice; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public Integer getTradeCount() { return tradeCount; }
    public void setTradeCount(Integer tradeCount) { this.tradeCount = tradeCount; }
}
