package com.sentinel_trade.detection;

import com.sentinel_trade.model.Alert;
import com.sentinel_trade.model.Alert.AlertType;
import com.sentinel_trade.model.Alert.Severity;
import com.sentinel_trade.model.TickData;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects two types of anomalies:
 *
 * 1. Large Order: single trade amount (price × quantity) > 50,000 USDT
 * 2. Flash Crash: price change > 2% within a 10-second sliding window
 *
 * Requirements: 3.1, 3.2
 */
public class AnomalyDetector extends KeyedProcessFunction<String, TickData, Alert> {

    private static final Logger LOG = LoggerFactory.getLogger(AnomalyDetector.class);

    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal FLASH_CRASH_THRESHOLD = new BigDecimal("0.02"); // 2%
    private static final long FLASH_CRASH_WINDOW_MS = 10_000L; // 10 seconds

    /** Stores (timestamp, price) pairs within the flash-crash window. */
    private transient ListState<long[]> priceHistory;

    @Override
    public void open(Configuration parameters) throws Exception {
        ListStateDescriptor<long[]> descriptor =
                new ListStateDescriptor<>("price-history", long[].class);
        priceHistory = getRuntimeContext().getListState(descriptor);
    }

    @Override
    public void processElement(TickData tick,
                               Context ctx,
                               Collector<Alert> out) throws Exception {
        detectLargeOrder(tick, out);
        detectFlashCrash(tick, ctx, out);
    }

    // -----------------------------------------------------------------------
    // Large order detection
    // -----------------------------------------------------------------------

    private void detectLargeOrder(TickData tick, Collector<Alert> out) {
        BigDecimal amount = tick.getAmount();
        if (amount.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            Alert alert = new Alert(
                    AlertType.LARGE_ORDER,
                    tick.getSymbol(),
                    tick.getTimestamp(),
                    Severity.HIGH,
                    tick.getPrice(),
                    tick.getQuantity()
            );
            LOG.info("LARGE_ORDER alert: symbol={} amount={} tradeId={}",
                    tick.getSymbol(), amount, tick.getTradeId());
            out.collect(alert);
        }
    }

    // -----------------------------------------------------------------------
    // Flash crash detection
    // -----------------------------------------------------------------------

    private void detectFlashCrash(TickData tick,
                                   Context ctx,
                                   Collector<Alert> out) throws Exception {
        long now = tick.getTimestamp();
        long windowStart = now - FLASH_CRASH_WINDOW_MS;

        // Encode price as scaled long (8 decimal places) to store in ListState<long[]>
        long priceScaled = tick.getPrice().scaleByPowerOfTen(8).longValue();
        priceHistory.add(new long[]{now, priceScaled});

        // Collect prices within the window
        List<long[]> inWindow = new ArrayList<>();
        for (long[] entry : priceHistory.get()) {
            if (entry[0] >= windowStart) {
                inWindow.add(entry);
            }
        }

        // Evict old entries
        priceHistory.clear();
        for (long[] entry : inWindow) {
            priceHistory.add(entry);
        }

        if (inWindow.size() < 2) {
            return;
        }

        // Find min and max price in window
        long minScaled = Long.MAX_VALUE;
        long maxScaled = Long.MIN_VALUE;
        for (long[] entry : inWindow) {
            if (entry[1] < minScaled) minScaled = entry[1];
            if (entry[1] > maxScaled) maxScaled = entry[1];
        }

        BigDecimal minPrice = BigDecimal.valueOf(minScaled, 8);
        BigDecimal maxPrice = BigDecimal.valueOf(maxScaled, 8);

        if (minPrice.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal changeRate = maxPrice.subtract(minPrice)
                .divide(minPrice, 8, RoundingMode.HALF_UP);

        if (changeRate.compareTo(FLASH_CRASH_THRESHOLD) > 0) {
            Alert alert = new Alert(
                    AlertType.FLASH_CRASH,
                    tick.getSymbol(),
                    tick.getTimestamp(),
                    Severity.CRITICAL,
                    tick.getPrice(),
                    tick.getQuantity()
            );
            LOG.info("FLASH_CRASH alert: symbol={} changeRate={} tradeId={}",
                    tick.getSymbol(), changeRate, tick.getTradeId());
            out.collect(alert);
        }
    }
}
