package com.sentinel_trade.aggregation;

import com.sentinel_trade.model.KLine;
import com.sentinel_trade.model.TickData;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KLineAggregator.
 *
 * Property 5: OHLC 计算正确性
 * Validates: Requirements 2.2, 10.1
 */
class KLineAggregatorTest {

    // -----------------------------------------------------------------------
    // Unit tests
    // -----------------------------------------------------------------------

    @Test
    void singleTickProducesCorrectOHLC() {
        TickData tick = tick("BTCUSDT", "45000", "1.0", 1000L, "t1");
        KLine kline = aggregate(Collections.singletonList(tick));

        assertNotNull(kline);
        assertEquals(new BigDecimal("45000"), kline.getOpen());
        assertEquals(new BigDecimal("45000"), kline.getHigh());
        assertEquals(new BigDecimal("45000"), kline.getLow());
        assertEquals(new BigDecimal("45000"), kline.getClose());
        assertEquals(new BigDecimal("1.0"), kline.getVolume());
        assertEquals(1, kline.getTradeCount());
    }

    @Test
    void openIsFirstTickByTimestamp() {
        List<TickData> ticks = Arrays.asList(
                tick("BTCUSDT", "45200", "1.0", 2000L, "t2"),
                tick("BTCUSDT", "45000", "1.0", 1000L, "t1"),  // earliest
                tick("BTCUSDT", "45100", "1.0", 3000L, "t3")
        );
        KLine kline = aggregate(ticks);
        assertEquals(new BigDecimal("45000"), kline.getOpen());
    }

    @Test
    void closeIsLastTickByTimestamp() {
        List<TickData> ticks = Arrays.asList(
                tick("BTCUSDT", "45000", "1.0", 1000L, "t1"),
                tick("BTCUSDT", "45100", "1.0", 3000L, "t3"),  // latest
                tick("BTCUSDT", "45200", "1.0", 2000L, "t2")
        );
        KLine kline = aggregate(ticks);
        assertEquals(new BigDecimal("45100"), kline.getClose());
    }

    @Test
    void highIsMaxPrice() {
        List<TickData> ticks = Arrays.asList(
                tick("BTCUSDT", "45000", "1.0", 1000L, "t1"),
                tick("BTCUSDT", "46000", "1.0", 2000L, "t2"),
                tick("BTCUSDT", "44000", "1.0", 3000L, "t3")
        );
        KLine kline = aggregate(ticks);
        assertEquals(new BigDecimal("46000"), kline.getHigh());
    }

    @Test
    void lowIsMinPrice() {
        List<TickData> ticks = Arrays.asList(
                tick("BTCUSDT", "45000", "1.0", 1000L, "t1"),
                tick("BTCUSDT", "46000", "1.0", 2000L, "t2"),
                tick("BTCUSDT", "44000", "1.0", 3000L, "t3")
        );
        KLine kline = aggregate(ticks);
        assertEquals(new BigDecimal("44000"), kline.getLow());
    }

    @Test
    void volumeIsSumOfQuantities() {
        List<TickData> ticks = Arrays.asList(
                tick("BTCUSDT", "45000", "1.5", 1000L, "t1"),
                tick("BTCUSDT", "45100", "2.5", 2000L, "t2"),
                tick("BTCUSDT", "45200", "1.0", 3000L, "t3")
        );
        KLine kline = aggregate(ticks);
        assertEquals(new BigDecimal("5.0"), kline.getVolume());
    }

    @Test
    void emptyWindowProducesNoOutput() {
        KLine kline = aggregate(Collections.emptyList());
        assertNull(kline);
    }

    // -----------------------------------------------------------------------
    // Property 5: OHLC 计算正确性
    // Validates: Requirements 2.2, 10.1
    // -----------------------------------------------------------------------

    /**
     * Feature: sentinel-trade, Property 5: OHLC 计算正确性
     *
     * For any non-empty sequence of TickData:
     * - Open  = price of first tick (by timestamp)
     * - High  = max price
     * - Low   = min price
     * - Close = price of last tick (by timestamp)
     * - Volume = sum of quantities
     */
    @Property(tries = 200)
    void ohlcCorrectness(@ForAll @Size(min = 1, max = 50) List<@From("validTicks") TickData> ticks) {

        KLine kline = aggregate(ticks);
        assertNotNull(kline, "Non-empty tick list must produce a KLine");

        // Sort by timestamp to determine expected open/close
        List<TickData> sorted = new ArrayList<>(ticks);
        sorted.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        BigDecimal expectedOpen  = sorted.get(0).getPrice();
        BigDecimal expectedClose = sorted.get(sorted.size() - 1).getPrice();
        BigDecimal expectedHigh  = sorted.stream().map(TickData::getPrice).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal expectedLow   = sorted.stream().map(TickData::getPrice).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal expectedVol   = sorted.stream().map(TickData::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(expectedOpen,  kline.getOpen(),   "Open must be first tick price");
        assertEquals(expectedClose, kline.getClose(),  "Close must be last tick price");
        assertEquals(expectedHigh,  kline.getHigh(),   "High must be max price");
        assertEquals(expectedLow,   kline.getLow(),    "Low must be min price");
        assertEquals(0, expectedVol.compareTo(kline.getVolume()), "Volume must be sum of quantities");
        assertEquals(ticks.size(), kline.getTradeCount(), "TradeCount must equal number of ticks");
    }

    @Provide
    Arbitrary<TickData> validTicks() {
        return Combinators.combine(
                Arbitraries.longs().between(1_000L, 1_000_000L),
                Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("100000")).ofScale(2),
                Arbitraries.bigDecimals().between(new BigDecimal("0.001"), new BigDecimal("100")).ofScale(3),
                Arbitraries.integers().between(1, 99999)
        ).as((ts, price, qty, id) ->
                new TickData("BTCUSDT", price, qty, ts, "t" + id, false)
        );
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static TickData tick(String symbol, String price, String qty, long ts, String id) {
        return new TickData(symbol, new BigDecimal(price), new BigDecimal(qty), ts, id, false);
    }

    /** Delegates to the pure static method — no Flink context needed. */
    private static KLine aggregate(List<TickData> ticks) {
        return KLineAggregator.computeKLine("BTCUSDT", "1m", 0L, 59_999L, ticks);
    }
}
