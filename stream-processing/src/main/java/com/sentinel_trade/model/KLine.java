package com.sentinel_trade.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * K-Line (candlestick) aggregation result.
 *
 * Requirements: 2.2, 10.1
 */
public class KLine {

    private String symbol;
    private String interval;      // "1m", "5m", "1h"
    private long openTime;        // window start (ms)
    private long closeTime;       // window end (ms)
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    private int tradeCount;

    /** Required no-arg constructor for Flink POJO serialization. */
    public KLine() {}

    public KLine(String symbol, String interval, long openTime, long closeTime,
                 BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                 BigDecimal volume, int tradeCount) {
        this.symbol = symbol;
        this.interval = interval;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.tradeCount = tradeCount;
    }

    // -----------------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------------

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }

    public long getOpenTime() { return openTime; }
    public void setOpenTime(long openTime) { this.openTime = openTime; }

    public long getCloseTime() { return closeTime; }
    public void setCloseTime(long closeTime) { this.closeTime = closeTime; }

    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }

    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }

    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }

    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }

    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }

    public int getTradeCount() { return tradeCount; }
    public void setTradeCount(int tradeCount) { this.tradeCount = tradeCount; }

    // -----------------------------------------------------------------------
    // equals / hashCode / toString
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KLine)) return false;
        KLine that = (KLine) o;
        return openTime == that.openTime
                && closeTime == that.closeTime
                && tradeCount == that.tradeCount
                && Objects.equals(symbol, that.symbol)
                && Objects.equals(interval, that.interval)
                && Objects.equals(open, that.open)
                && Objects.equals(high, that.high)
                && Objects.equals(low, that.low)
                && Objects.equals(close, that.close)
                && Objects.equals(volume, that.volume);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, interval, openTime, closeTime,
                open, high, low, close, volume, tradeCount);
    }

    @Override
    public String toString() {
        return "KLine{symbol='" + symbol + "', interval='" + interval
                + "', openTime=" + openTime + ", open=" + open
                + ", high=" + high + ", low=" + low + ", close=" + close
                + ", volume=" + volume + ", tradeCount=" + tradeCount + '}';
    }
}
