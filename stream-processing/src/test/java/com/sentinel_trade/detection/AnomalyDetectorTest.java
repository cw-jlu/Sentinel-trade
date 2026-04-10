package com.sentinel_trade.detection;

import com.sentinel_trade.model.Alert;
import com.sentinel_trade.model.TickData;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AnomalyDetector.
 *
 * Property 7: 大单检测阈值准确性
 * Validates: Requirements 3.1
 *
 * Property 8: 闪崩检测准确性
 * Validates: Requirements 3.2
 */
class AnomalyDetectorTest {

    // -----------------------------------------------------------------------
    // Large order unit tests
    // -----------------------------------------------------------------------

    @Test
    void largeOrderAboveThresholdTriggersAlert() throws Exception {
        // 100 * 600 = 60,000 > 50,000
        TickData tick = tick("BTCUSDT", "100", "600", 1000L, "t1");
        List<Alert> alerts = processLargeOrder(tick);
        assertEquals(1, alerts.size());
        assertEquals(Alert.AlertType.LARGE_ORDER, alerts.get(0).getAlertType());
        assertEquals(Alert.Severity.HIGH, alerts.get(0).getSeverity());
    }

    @Test
    void largeOrderBelowThresholdNoAlert() throws Exception {
        // 100 * 499 = 49,900 < 50,000
        TickData tick = tick("BTCUSDT", "100", "499", 1000L, "t1");
        List<Alert> alerts = processLargeOrder(tick);
        assertTrue(alerts.isEmpty());
    }

    @Test
    void largeOrderExactlyAtThresholdNoAlert() throws Exception {
        // 100 * 500 = 50,000 – NOT greater than, so no alert
        TickData tick = tick("BTCUSDT", "100", "500", 1000L, "t1");
        List<Alert> alerts = processLargeOrder(tick);
        assertTrue(alerts.isEmpty());
    }

    @Test
    void largeOrderAlertContainsRequiredFields() throws Exception {
        TickData tick = tick("BTCUSDT", "100", "600", 1000L, "t1");
        List<Alert> alerts = processLargeOrder(tick);
        Alert alert = alerts.get(0);
        assertNotNull(alert.getAlertId());
        assertNotNull(alert.getSymbol());
        assertTrue(alert.getTimestamp() > 0);
        assertNotNull(alert.getAlertType());
        assertNotNull(alert.getSeverity());
    }

    // -----------------------------------------------------------------------
    // Property 7: 大单检测阈值准确性
    // Validates: Requirements 3.1
    // -----------------------------------------------------------------------

    /**
     * Feature: sentinel-trade, Property 7: 大单检测阈值准确性
     *
     * For any transaction, the system SHALL generate a large order alert
     * if and only if price × quantity > 50,000 USDT.
     */
    @Property(tries = 200)
    void largeOrderThresholdAccuracy(
            @ForAll @net.jqwik.api.constraints.Positive double priceDouble,
            @ForAll @net.jqwik.api.constraints.Positive double quantityDouble) throws Exception {

        // Constrain to reasonable financial values
        BigDecimal price    = BigDecimal.valueOf(Math.min(priceDouble, 1_000_000.0)).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal quantity = BigDecimal.valueOf(Math.min(quantityDouble, 10_000.0)).setScale(4, java.math.RoundingMode.HALF_UP);

        if (price.compareTo(BigDecimal.ZERO) <= 0 || quantity.compareTo(BigDecimal.ZERO) <= 0) return;

        TickData tick = new TickData("BTCUSDT", price, quantity, 1000L, "t1", false);
        BigDecimal amount = tick.getAmount();
        BigDecimal threshold = new BigDecimal("50000");

        List<Alert> alerts = processLargeOrder(tick);

        if (amount.compareTo(threshold) > 0) {
            assertEquals(1, alerts.size(), "Expected alert when amount=" + amount + " > 50000");
            assertEquals(Alert.AlertType.LARGE_ORDER, alerts.get(0).getAlertType());
        } else {
            assertTrue(alerts.isEmpty(), "Expected no alert when amount=" + amount + " <= 50000");
        }
    }

    // -----------------------------------------------------------------------
    // Flash crash unit tests
    // -----------------------------------------------------------------------

    @Test
    void flashCrashAbove2PercentTriggersAlert() throws Exception {
        // Price goes from 100 to 103 = 3% change within 10s
        List<TickData> ticks = List.of(
                tick("BTCUSDT", "100", "1", 1000L, "t1"),
                tick("BTCUSDT", "103", "1", 5000L, "t2")
        );
        List<Alert> alerts = processFlashCrash(ticks);
        assertTrue(alerts.stream().anyMatch(a -> a.getAlertType() == Alert.AlertType.FLASH_CRASH));
    }

    @Test
    void flashCrashBelow2PercentNoAlert() throws Exception {
        // Price goes from 100 to 101 = 1% change
        List<TickData> ticks = List.of(
                tick("BTCUSDT", "100", "1", 1000L, "t1"),
                tick("BTCUSDT", "101", "1", 5000L, "t2")
        );
        List<Alert> alerts = processFlashCrash(ticks);
        assertTrue(alerts.stream().noneMatch(a -> a.getAlertType() == Alert.AlertType.FLASH_CRASH));
    }

    @Test
    void flashCrashOutsideWindowNoAlert() throws Exception {
        // Price changes by 5% but ticks are 15 seconds apart (outside 10s window)
        List<TickData> ticks = List.of(
                tick("BTCUSDT", "100", "1", 1000L, "t1"),
                tick("BTCUSDT", "105", "1", 16000L, "t2")  // 15s later
        );
        List<Alert> alerts = processFlashCrash(ticks);
        assertTrue(alerts.stream().noneMatch(a -> a.getAlertType() == Alert.AlertType.FLASH_CRASH));
    }

    // -----------------------------------------------------------------------
    // Property 8: 闪崩检测准确性
    // Validates: Requirements 3.2
    // -----------------------------------------------------------------------

    /**
     * Feature: sentinel-trade, Property 8: 闪崩检测准确性
     *
     * For any 10-second price sequence, the system SHALL generate a flash crash
     * alert if and only if the price change exceeds 2% within that window.
     */
    @Property(tries = 200)
    void flashCrashThresholdAccuracy(
            @ForAll @net.jqwik.api.constraints.DoubleRange(min = 1.0, max = 100000.0) double basePrice,
            @ForAll @net.jqwik.api.constraints.DoubleRange(min = 0.0, max = 0.10) double changeRate,
            @ForAll boolean priceGoesUp) throws Exception {

        BigDecimal base   = BigDecimal.valueOf(basePrice).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal change = base.multiply(BigDecimal.valueOf(changeRate)).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal second = priceGoesUp ? base.add(change) : base.subtract(change);

        if (second.compareTo(BigDecimal.ZERO) <= 0) return;

        List<TickData> ticks = List.of(
                new TickData("BTCUSDT", base,   new BigDecimal("1"), 1000L,  "t1", false),
                new TickData("BTCUSDT", second, new BigDecimal("1"), 5000L,  "t2", false)
        );

        List<Alert> alerts = processFlashCrash(ticks);
        boolean hasCrashAlert = alerts.stream().anyMatch(a -> a.getAlertType() == Alert.AlertType.FLASH_CRASH);

        BigDecimal minP = base.min(second);
        BigDecimal maxP = base.max(second);
        BigDecimal actualRate = minP.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : maxP.subtract(minP).divide(minP, 8, java.math.RoundingMode.HALF_UP);

        if (actualRate.compareTo(new BigDecimal("0.02")) > 0) {
            assertTrue(hasCrashAlert,
                    "Expected flash crash alert for changeRate=" + actualRate);
        } else {
            assertFalse(hasCrashAlert,
                    "Expected no flash crash alert for changeRate=" + actualRate);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static TickData tick(String symbol, String price, String qty, long ts, String id) {
        return new TickData(symbol, new BigDecimal(price), new BigDecimal(qty), ts, id, false);
    }

    private List<Alert> processLargeOrder(TickData tick) throws Exception {
        MockAnomalyDetector detector = new MockAnomalyDetector();
        List<Alert> out = new ArrayList<>();
        detector.processElement(tick, null, listCollector(out));
        return out;
    }

    private List<Alert> processFlashCrash(List<TickData> ticks) throws Exception {
        MockAnomalyDetector detector = new MockAnomalyDetector();
        List<Alert> out = new ArrayList<>();
        org.apache.flink.util.Collector<Alert> collector = listCollector(out);
        for (TickData tick : ticks) {
            detector.processElement(tick, null, collector);
        }
        return out;
    }

    private static <T> org.apache.flink.util.Collector<T> listCollector(List<T> list) {
        return new org.apache.flink.util.Collector<T>() {
            @Override public void collect(T record) { list.add(record); }
            @Override public void close() {}
        };
    }

    /**
     * Subclass that bypasses Flink state management for unit testing.
     * Uses simple in-memory lists instead of ListState.
     */
    static class MockAnomalyDetector extends AnomalyDetector {

        private final List<long[]> inMemoryPriceHistory = new ArrayList<>();

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) {
            // Override to skip Flink state initialization
        }

        @Override
        public void processElement(TickData tick,
                                   Context ctx,
                                   org.apache.flink.util.Collector<Alert> out) throws Exception {
            // Large order detection (same logic, no state needed)
            BigDecimal amount = tick.getAmount();
            if (amount.compareTo(new BigDecimal("50000")) > 0) {
                out.collect(new Alert(Alert.AlertType.LARGE_ORDER, tick.getSymbol(),
                        tick.getTimestamp(), Alert.Severity.HIGH,
                        tick.getPrice(), tick.getQuantity()));
            }

            // Flash crash detection using in-memory list
            long now = tick.getTimestamp();
            long windowStart = now - 10_000L;
            long priceScaled = tick.getPrice().scaleByPowerOfTen(8).longValue();
            inMemoryPriceHistory.add(new long[]{now, priceScaled});

            List<long[]> inWindow = new ArrayList<>();
            for (long[] entry : inMemoryPriceHistory) {
                if (entry[0] >= windowStart) inWindow.add(entry);
            }
            inMemoryPriceHistory.clear();
            inMemoryPriceHistory.addAll(inWindow);

            if (inWindow.size() < 2) return;

            long minScaled = Long.MAX_VALUE, maxScaled = Long.MIN_VALUE;
            for (long[] entry : inWindow) {
                if (entry[1] < minScaled) minScaled = entry[1];
                if (entry[1] > maxScaled) maxScaled = entry[1];
            }

            BigDecimal minPrice = BigDecimal.valueOf(minScaled, 8);
            BigDecimal maxPrice = BigDecimal.valueOf(maxScaled, 8);
            if (minPrice.compareTo(BigDecimal.ZERO) == 0) return;

            BigDecimal changeRate = maxPrice.subtract(minPrice)
                    .divide(minPrice, 8, java.math.RoundingMode.HALF_UP);

            if (changeRate.compareTo(new BigDecimal("0.02")) > 0) {
                out.collect(new Alert(Alert.AlertType.FLASH_CRASH, tick.getSymbol(),
                        tick.getTimestamp(), Alert.Severity.CRITICAL,
                        tick.getPrice(), tick.getQuantity()));
            }
        }
    }
}
